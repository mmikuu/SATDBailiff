package se.rit.edu.git;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import se.rit.edu.satd.detector.SATDDetector;
import se.rit.edu.util.ElapsedTimer;
import se.rit.edu.satd.comment.GroupedComment;
import se.rit.edu.util.JavaParseUtil;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RepositoryCommitReference {

    private RevCommit commit;
    private Git gitInstance;
    private String projectName;
    private String projectURI;
    private Map<String, List<GroupedComment>> satdOccurrences = null;

    private ElapsedTimer timer = null;

    RepositoryCommitReference(Git gitInstance, String projectName, String projectURI, RevCommit commit) {
        this.commit = commit;
        this.projectName = projectName;
        this.projectURI = projectURI;
        this.gitInstance = gitInstance;
    }

    public String getProjectName() {
        return this.projectName;
    }

    public String getProjectURI() {
        return this.projectURI;
    }

    public RevCommit getCommit() {
        return this.commit;
    }

    public Git getGitInstance() {
        return this.gitInstance;
    }

    public List<RepositoryCommitReference> getParentCommitReferences() {
        final RevWalk rw = new RevWalk(this.gitInstance.getRepository());
        return Arrays.stream(this.commit.getParents())
                .map(RevCommit::toObjectId)
                .map(id -> {
                        try {
                            return rw.parseCommit(id);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }})
                .map(parent -> new RepositoryCommitReference(
                        this.gitInstance,
                        this.projectName,
                        this.projectURI,
                        parent
                ))
                .collect(Collectors.toList());
    }

    public Map<String, List<GroupedComment>> getFilesToSAIDOccurrences(SATDDetector detector, List<String> filesToSearch){

        if( this.satdOccurrences != null ) {
            return this.satdOccurrences;
        }

        this.startSATDParseTimer();

        final TreeWalk thisRepoWalker = GitUtil.getTreeWalker(this.gitInstance, this.commit);
        final Map<String, List<GroupedComment>> filesToSATDMap = new HashMap<>();
        try {
            // Walk through each Java file in the repository at the time of the commit
            while (thisRepoWalker.next()) {

                if( filesToSearch.contains(thisRepoWalker.getPathString())) {
                    // Get loader to load file contents into memory
                    final ObjectLoader fileLoader = this.gitInstance.getRepository()
                            .open(thisRepoWalker.getObjectId(0));
                    // Parse Java file for SATD and add it to the map
                    filesToSATDMap.put(
                            thisRepoWalker.getPathString(),
                            JavaParseUtil.parseFileForComments(fileLoader.openStream()).stream()
                                    .filter(groupedComment -> detector.isSATD(groupedComment.getComment()))
                                    .collect(Collectors.toList())
                    );
                }
            }
        } catch (MissingObjectException | IncorrectObjectTypeException | CorruptObjectException e) {
            System.err.println("Exception in getting tree walker.");
            e.printStackTrace();
        } catch (IOException e) {
            System.err.println("IOException in getting tree walker.");
            e.printStackTrace();
        }

        this.endSATDParseTimer();
        // Store a reference to be returned later to avoid parsing more than once
        this.satdOccurrences = filesToSATDMap;
        return filesToSATDMap;
    }

    /**
     * Overwrites and starts a time to record the time it takes to locate SATD In the repository
     * at the given commit
     */
    private void startSATDParseTimer() {
        this.timer = new ElapsedTimer();
        this.timer.start();
    }

    /**
     * Ends the timer, and reports the time it took to parse the SATD in the repository
     */
    private void endSATDParseTimer() {
        if( this.timer != null ) {
            this.timer.end();
            System.out.println(String.format("Finished finding SATD in %s/commit/%s in %,dms",
                    this.projectName, this.commit.getName(), this.timer.readMS()));
        }
    }
}
