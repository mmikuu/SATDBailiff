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
import edu.rit.se.satd.model.SATDInstanceMappingDB;
import edu.rit.se.satd.writer.OutputWriter;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.errors.GitAPIException;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A class which contains high-level logic for mining SATD Instances from a git repository.
 */
public class RemoveSATDMiner {
    //TODO globalにDBのやつブッコム？
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

    private Map<SATDInstanceInFile, Integer> satdInstanceMappings = new HashMap<>();

    private ElapsedTimer timer = new ElapsedTimer();

    private int curSATDId;

    private SATDInstanceMappingDB mappingDB;

    @Getter
    private static boolean errorOutputEnabled = true;

//    private final String dbURI;
//    private final String user;
//    private final String pass;
//    private final ScheduledThreadPoolExecutor finalWriteExecutor;

    public RemoveSATDMiner() throws IOException, SQLException {
        this.mappingDB = new SATDInstanceMappingDB();
    }

    public void mapInstanceToNewInstanceId(SATDDifference diff) throws SQLException {
        List<SATDInstance> satdInstances = diff.getSatdInstances();
        System.out.println("mapInstanceToNewInstanceIdとおった");

        for (SATDInstance satdInstance : satdInstances) {
            System.out.println("resolution" + satdInstance.getResolution());
            switch (satdInstance.getResolution()) {
                case SATD_REMOVED:
                case FILE_REMOVED:
                case SATD_MOVED_FILE:
                    System.out.println("REMOVE処理");
                    int projectID = mappingDB.checkProjectID(diff.getProjectName(), diff.getProjectURI());
                    String oldCommitId = mappingDB.getCommitId(new CommitMetaData(diff.getOldCommit()), projectID, false);
                    String newCommitId = mappingDB.getCommitId(new CommitMetaData(diff.getNewCommit()), projectID, false);
                    //並列化がうまく行っているか確認するため，
                    System.out.println("oldCommitId" + oldCommitId);
                    System.out.println("newCommitId" + newCommitId);

                    SATDInstanceInFile oldInstanceInfile = satdInstance.getOldInstance();
                    int oldInstanceHashcode = oldInstanceInfile.hashCode();
                    String instanceId = null;
                    try {
                        if (mappingDB.checkInstanceID(oldInstanceHashcode) == null) {
                            instanceId = mappingDB.checkInstanceID(oldInstanceHashcode);
                            int newFileId = mappingDB.getSATDInFileId(satdInstance, false, oldInstanceHashcode, false);
                            int oldFileId = mappingDB.getSATDInFileId(satdInstance, true, oldInstanceHashcode, false);
                            mappingDB.getSATDInstanceId(satdInstance, newCommitId, oldCommitId, newFileId, oldFileId, projectID, instanceId, oldInstanceHashcode);

                        } else {
                            if (satdInstance.getResolution().equals(SATDInstance.SATDResolution.SATD_MOVED_FILE)) {
                                instanceId = null;
                                int newFileId = mappingDB.getSATDInFileId(satdInstance, false, oldInstanceHashcode, false);
                                int oldFileId = mappingDB.getSATDInFileId(satdInstance, true, oldInstanceHashcode, false);
                                mappingDB.getSATDInstanceId(satdInstance, newCommitId, oldCommitId, newFileId, oldFileId, projectID, instanceId, oldInstanceHashcode);
                            } else { // SATD_REMOVEDの処理
                                instanceId = mappingDB.checkInstanceID(oldInstanceHashcode);
                                int newFileId = mappingDB.getSATDInFileId(satdInstance, false, oldInstanceHashcode, false);
                                int oldFileId = mappingDB.getSATDInFileId(satdInstance, true, oldInstanceHashcode, false);
                                mappingDB.getSATDInstanceId(satdInstance, newCommitId, oldCommitId, newFileId, oldFileId, projectID, instanceId, oldInstanceHashcode);
                            }
                        }
                    } catch (NullPointerException e) {
                        System.err.println("NullPointerException encountered:");
                        System.err.println("satdInstance: " + satdInstance);
                        System.err.println("newCommitId: " + newCommitId);
                        System.err.println("oldCommitId: " + oldCommitId);
                        System.err.println("projectID: " + projectID);
                        System.err.println("instanceId: " + instanceId);
                        System.err.println("oldInstanceHashcode: " + oldInstanceHashcode);
                        e.printStackTrace();
                        throw e;
                    }

                    if (isErrorOutputEnabled()) {
                        System.err.println("\nMultiple SATD_Delete instances for " +
                                satdInstance.getOldInstance().toString());
                    }
            }
//                    satdInstance.setId(Integer.parseInt(instanceId));
            break;
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
                 validPairs.add(diffPair);
             }
             times = times+1;
         }
         return validPairs;
    }
}
