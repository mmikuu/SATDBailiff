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
public class ChangeSATDMiner {
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

    public ChangeSATDMiner() throws IOException, SQLException {
        this.mappingDB = new SATDInstanceMappingDB();
    }


    public void mapInstanceToNewInstanceId(SATDDifference diff) throws SQLException {
        List<SATDInstance> satdInstances = diff.getSatdInstances();
        System.out.println("mapInstanceToNewInstanceIdとおった");

        for (SATDInstance satdInstance : satdInstances) {
            System.out.println("resolution"+satdInstance.getResolution());
            switch (satdInstance.getResolution()) {
                case SATD_CHANGED: case FILE_PATH_CHANGED: case CLASS_OR_METHOD_CHANGED:

                    int projectID = mappingDB.checkProjectID(diff.getProjectName(),diff.getProjectURI());

                    SATDInstanceInFile oldInstanceInfile = satdInstance.getOldInstance();
                    int oldInstanceHashcode = oldInstanceInfile.hashCode();
                    String instanceId = null;

                    if ( mappingDB.checkInstanceID(oldInstanceHashcode) == null ){
                        //instanceがない場合まだDBに登録されていない親のchangeが登録されていない
                        //待機DBにchangeを登録する．．？その際に親のインスタのhashcode

                        //CommitIDをwaitCommitのDBに登録
                        String oldCommitId = mappingDB.getCommitId(new CommitMetaData(diff.getOldCommit()),projectID,true);
                        String newCommitId = mappingDB.getCommitId(new CommitMetaData(diff.getNewCommit()),projectID,true);


                        //fileをwaitSATDInFIleIDのDBに登録
                        int newFileId = mappingDB.getSATDInFileId(satdInstance,false,oldInstanceHashcode,true);
                        int oldFileId = mappingDB.getSATDInFileId(satdInstance,true,oldInstanceHashcode,true);


                        //TODO file,commitなどのSATD情報をSATDのDBに登録
                        mappingDB.getWaitSATDInstanceId(satdInstance,newCommitId,oldCommitId,newFileId,oldFileId,projectID,oldInstanceHashcode);


                        //TODO writeWaitChangeのDBからwaitCommit,waitSATD,waitSATDInFIleが参照できるか確認すること
                        String resolution = String.valueOf(satdInstance.getResolution());
                        mappingDB.writeWaitChange(oldInstanceHashcode,resolution,newCommitId,oldCommitId,newFileId,oldFileId,projectID);

                    } else {
                        //nullではないinstanceIdが入っている
                        instanceId = mappingDB.checkInstanceID(oldInstanceHashcode);

                        //CommitIDをCommitのDBに登録
                        String oldCommitId = mappingDB.getCommitId(new CommitMetaData(diff.getOldCommit()),projectID,false);
                        String newCommitId = mappingDB.getCommitId(new CommitMetaData(diff.getNewCommit()),projectID,false);

                        //fileをSATDInFIleIDのDBに登録
                        int newFileId = mappingDB.getSATDInFileId(satdInstance,false,oldInstanceHashcode,false);
                        int oldFileId = mappingDB.getSATDInFileId(satdInstance,true,oldInstanceHashcode,false);
                        //file,commitなどのSATD情報をSATDのDBに登録
                        mappingDB.getSATDInstanceId(satdInstance,newCommitId,oldCommitId,newFileId,oldFileId,projectID,instanceId,oldInstanceHashcode);

                        if (isErrorOutputEnabled()) {
                            System.err.println("\nMultiple SATD_CHANGE instances for " +
                                    satdInstance.getOldInstance().toString());
                        }
                        this.status.addErrorEncountered();
                    }
                    break;
            }
        }
    }

    private int getNewSATDId() {
        return ++this.curSATDId;
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
