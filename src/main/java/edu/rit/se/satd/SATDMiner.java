package edu.rit.se.satd;

import edu.rit.se.git.DevNullCommitReference;
import edu.rit.se.git.GitUtil;
import edu.rit.se.git.RepositoryCommitReference;
import edu.rit.se.git.RepositoryInitializer;
import edu.rit.se.git.model.CommitMetaData;
import edu.rit.se.satd.detector.SATDDetector;
import edu.rit.se.satd.mining.RepositoryDiffMiner;
import edu.rit.se.satd.mining.ui.ElapsedTimer;
import edu.rit.se.satd.mining.ui.MinerStatus;
import edu.rit.se.satd.model.SATDDifference;
import edu.rit.se.satd.model.SATDInstance;
import edu.rit.se.satd.model.SATDInstanceInFile;
import edu.rit.se.satd.writer.OutputWriter;
import jp.naist.se.commentlister.lexer.Python3Parser;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.builder.Diff;
import org.eclipse.jgit.api.errors.GitAPIException;
import weka.filters.unsupervised.attribute.Remove;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A class which contains high-level logic for mining SATD Instances from a git repository.
 */
public class SATDMiner {

    @NonNull
    private String repositoryURI;
    @NonNull
    private SATDDetector satdDetector;

    @Setter
    private String githubUsername = null;
    @Setter
    private String githubPassword = null;

    // A reference to the repository initializes. Stored so it can be cleaned
    // once mining has completed
    private RepositoryInitializer repo;

    // Miner status for console output
    private MinerStatus status;

    private String aimDir;

    private Map<SATDInstanceInFile, Integer> satdInstanceMappings = new HashMap<>();

    private ElapsedTimer timer = new ElapsedTimer();

    @Getter
    private static boolean errorOutputEnabled = true;

    public SATDMiner(String repositoryURI, SATDDetector satdDetector,String aimDir) {
        this.repositoryURI = repositoryURI;
        this.satdDetector = satdDetector;
        this.status = new MinerStatus(GitUtil.getRepoNameFromGithubURI(this.repositoryURI));
        this.aimDir = aimDir;
    }

    public void disableStatusOutput() {
        this.status.setOutputEnabled(false);
    }

    public static void disableErrorOutput() {
        errorOutputEnabled = false;
    }

    public RepositoryCommitReference getBaseCommit(String head) throws GitAPIException, IOException {
        this.timer.start();
        this.status.beginInitialization();
        if( (repo == null || !repo.didInitialize()) &&
                !this.initializeRepo(this.githubUsername, this.githubPassword) ) {
            System.err.println("Repository failed to initialize");
            return null;
        }

        return this.repo.getMostRecentCommit(head);
    }

    /**
     * Cleans the repository that was mined by the Miner. This should delete all files created
     * by the miner.
     */
    public void cleanRepo() {
        this.status.beginCleanup();
        this.repo.cleanRepo();
        try {
            // Two files are created, so delete the parent as well
            FileUtils.deleteDirectory(new File(repo.getRepoDir()).getParentFile());
        } catch (IOException e) {
            System.err.println("Error in deleting cleaned git repo.");
            e.printStackTrace();
        }
        this.timer.end();
        this.status.setComplete(this.timer.readMS());
    }

    /**
     * Iterates over all supplied commits, and outputs a difference in SATD occurrences between
     * each adjacent diff reference in commitRefs
     * @param commitRef a list of supplied diff references to be diffed for SATD
     * @param writer an OutputWriter that will handle the output of the miner
     */
    public void writeRepoSATD(RepositoryCommitReference commitRef, OutputWriter writer,int startNum, int endNum, String typo,String aimDir) {
        if( commitRef == null ) {
            System.out.println("erroです");
            this.status.setError();
            return;
        }
        this.status.beginCalculatingDiffs();

        System.out.println("commitRef"+commitRef);

        final List<DiffPair> allDiffPairs =  this.getAllDiffPairs(commitRef);

        final List<DiffPair> validDiffPairs = getValidPairs(allDiffPairs,startNum,endNum);
        System.out.println("===========================");
        System.out.println("validDiffPAIRS"+validDiffPairs.size());
        System.out.println("===========================");
        this.status.beginMiningSATD();
        this.status.setNDiffsPromised(validDiffPairs.size());

        validDiffPairs.stream()
                .map(pair -> new RepositoryDiffMiner(pair.parentRepo, pair.repo, this.satdDetector))
                .map(repositoryDiffMiner -> {
                    this.status.setDisplayWindow(repositoryDiffMiner.getDiffString());
                    try {
                        return repositoryDiffMiner.mineDiff(aimDir);
                    } catch (GitAPIException e) {
                        throw new RuntimeException(e);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
//                .map(this::mapInstancesInDiffToPriorInstances)
                .forEach(diff -> {
                    try {
                        CommitSATDMiner commit = new CommitSATDMiner();
                        commit.mapInstanceToNewInstanceId(diff);
//                        List<SATDInstance> col = diff.getInstanc();
//                        col.forEach(instance -> {
//                            System.out.println("========================");
//                            System.out.println(instance.getInstance(false).getGroupComment().getComment());
//                            System.out.println(instance.getInstance(false).getGroupComment().getLine());
//                            System.out.println("========================");
//                            System.out.println(instance.getInstance(true).getGroupComment().getComment());
//                            System.out.println(instance.getInstance(true).getGroupComment().getLine());
//                        });
                        this.mapInstanceToNewInstanceId(diff,typo);
//                        writer.writeDiff(diff);
                        this.status.fulfilDiffPromise();
                    } catch (IOException e) {
                        this.status.addErrorEncountered();
                        System.err.println("Error writing diff: " + e.getLocalizedMessage());
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                });

    }

    private boolean initializeRepo(String username, String password) throws GitAPIException, IOException {
        this.repo = ( username != null && password != null ) ?
                new RepositoryInitializer(this.repositoryURI, GitUtil.getRepoNameFromGithubURI(this.repositoryURI),
                        username, password,this.aimDir):
                new RepositoryInitializer(this.repositoryURI, GitUtil.getRepoNameFromGithubURI(this.repositoryURI),this.aimDir);
        return this.repo.initRepo();
    }

    private List<DiffPair> getAllDiffPairs(RepositoryCommitReference curRef) {
        final Set<RepositoryCommitReference> visitedCommits = new HashSet<>();
        final Set<RepositoryCommitReference> allCommits = new HashSet<>();
        allCommits.add(curRef);
        // Continue until no new diff refs are found
        while( allCommits.size() > visitedCommits.size() ) {
            allCommits.addAll(
                    allCommits.stream()
                            .filter(ref -> !visitedCommits.contains(ref))
                            .peek(visitedCommits::add)
                            .map(RepositoryCommitReference::getParentCommitReferences)
                            .flatMap(Collection::stream)
                            .collect(Collectors.toSet())
            );
        }
        return allCommits.stream()
                // Only include non-merge commits
                .filter(ref -> ref.getParentCommitReferences().size() < 2)
                .flatMap(ref -> {
                    if( ref.getParentCommitReferences().isEmpty() ) {
                        return Stream.of(new DiffPair(ref, new DevNullCommitReference()));
                    } else {
                        return ref.getParentCommitReferences().stream()
                                .map(parent -> new DiffPair(ref, parent));
                    }
                })
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * Associates all SATDInstances in the diff object with other instances found
     * in this project and removes duplicate entries
     * @param diff an SATDDifference object
     * @return the SATDDifference object
     */
//    private SATDDifference mapInstancesInDiffToPriorInstances(SATDDifference diff) {
//        return diff.usingNewInstances(
//                diff.getSatdInstances().stream()
//                        .distinct()
//                        .map(this::mapInstanceToNewInstanceId)
//                        .collect(Collectors.toList())
//        );
//    }


    private void mapInstanceToNewInstanceId(SATDDifference diff, String typo) throws SQLException, IOException {
        //switch こまんど何何がADDEDだった場合
        switch (typo) {
            case "ADDED":
                AddSATDMiner add = new AddSATDMiner();
                add.mapInstanceToNewInstanceId(diff);
                break;
            case "CHANGED":
                ChangeSATDMiner change = new ChangeSATDMiner();
                change.mapInstanceToNewInstanceId(diff);
                break;
            case "REMOVED":
                RemoveSATDMiner remove = new RemoveSATDMiner();
                remove.mapInstanceToNewInstanceId(diff);
                break;
            default:
                CommitSATDMiner commit = new CommitSATDMiner();
                commit.mapInstanceToNewInstanceId(diff);
        }
    }

    @RequiredArgsConstructor
    public class DiffPair implements Comparable {

        @NonNull
        @Getter
        private RepositoryCommitReference repo;
        @NonNull
        @Getter
        private RepositoryCommitReference parentRepo;

        @Override
        public boolean equals(Object obj) {
            if( obj instanceof DiffPair ) {
                return this.repo.getCommitHash().equals(((DiffPair) obj).repo.getCommitHash()) &&
                        this.parentRepo.getCommitHash().equals(((DiffPair) obj).parentRepo.getCommitHash());
            }
            return false;
        }

        @Override
        public int hashCode() {
            return (this.parentRepo.getCommitHash() + this.repo.getCommitHash()).hashCode();
        }

        @Override
        public int compareTo(Object o) {
            if( o instanceof DiffPair ) {
                int commitTimeDiff = this.repo.getCommitTime() - ((DiffPair) o).repo.getCommitTime();
                // If the commits were committed at the same time, look at the authored date
                // to determine which came first
                if( commitTimeDiff == 0 ) {
                    return Long.compare(this.repo.getAuthoredTime(), ((DiffPair) o).repo.getAuthoredTime());
                }
                return commitTimeDiff;
            }
            return -1;
        }
    }

    public List<DiffPair> getValidPairs(List<DiffPair> allDiffPairs ,int startNum ,int endNum){
        List<DiffPair> validPairs = new ArrayList<>();
        int times = 0;

         for (DiffPair diffPair : allDiffPairs) {
             if(startNum <= times && endNum >= times){
                 System.out.println("===============");
                 validPairs.add(diffPair);
             }
             times = times+1;
         }
         return validPairs;
    }
}
