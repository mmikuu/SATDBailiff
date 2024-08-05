package edu.rit.se.satd;

import edu.rit.se.git.RepositoryInitializer;
import edu.rit.se.git.model.CommitMetaData;
import edu.rit.se.satd.detector.SATDDetector;
import edu.rit.se.satd.mining.ui.ElapsedTimer;
import edu.rit.se.satd.mining.ui.MinerStatus;
import edu.rit.se.satd.model.SATDDifference;
import edu.rit.se.satd.model.SATDInstance;
import edu.rit.se.satd.model.SATDInstanceInFile;
import edu.rit.se.satd.model.SATDInstanceMappingDB;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A class which contains high-level logic for mining SATD Instances from a git repository.
 */
public class AddSATDMiner {
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

    public AddSATDMiner() throws IOException, SQLException {
        this.mappingDB = new SATDInstanceMappingDB();
    }


    public void mapInstanceToNewInstanceId(SATDDifference diff) throws SQLException {
        List<SATDInstance> satdInstances = diff.getSatdInstances();
        System.out.println("mapInstanceToNewInstanceIdとおった");

        for (SATDInstance satdInstance : satdInstances) {
            System.out.println("resolution" + satdInstance.getResolution());
            switch (satdInstance.getResolution()) {
                case SATD_ADDED:

                    int projectID = mappingDB.checkProjectID(diff.getProjectName(), diff.getProjectURI());
                    String oldCommitId = mappingDB.getCommitId(new CommitMetaData(diff.getOldCommit()), projectID, false);
                    String newCommitId = mappingDB.getCommitId(new CommitMetaData(diff.getNewCommit()), projectID, false);
                    //並列化がうまく行っているか確認するため，
                    System.out.println("oldCommitId" + oldCommitId);
                    System.out.println("newCommitId" + newCommitId);

                    SATDInstanceInFile newInstanceInfile = satdInstance.getNewInstance();
                    int newInstanceHashcode = newInstanceInfile.hashCode();
                    String instanceId = null;

                    if (mappingDB.checkInstanceID(newInstanceHashcode) == null) {
                        instanceId = mappingDB.checkInstanceID(newInstanceHashcode);
                        int newFileId = mappingDB.getSATDInFileId(satdInstance, false, newInstanceHashcode, false);
                        int oldFileId = mappingDB.getSATDInFileId(satdInstance, true, newInstanceHashcode, false);
                        mappingDB.getSATDInstanceId(satdInstance, newCommitId, oldCommitId, newFileId, oldFileId, projectID, instanceId, newInstanceHashcode);

                    } else {
                        if (isErrorOutputEnabled()) {
                            System.err.println("\nMultiple SATD_ADDED instances for " +
                                    satdInstance.getOldInstance().toString());
                        }
                    }
                    //                    satdInstance.setId(Integer.parseInt(instanceId));
                    break;
            }
        }
    }
}