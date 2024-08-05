package edu.rit.se.satd;

import edu.rit.se.git.model.CommitMetaData;
import edu.rit.se.satd.model.SATDDifference;
import edu.rit.se.satd.model.SATDInstance;
import edu.rit.se.satd.model.SATDInstanceInFile;
import edu.rit.se.satd.model.SATDInstanceMappingDB;

import java.sql.SQLException;
import java.util.List;

public class CommitSATDMiner {
    private SATDInstanceMappingDB mappingDB;
    CommitSATDMiner() throws SQLException {
        this.mappingDB = new SATDInstanceMappingDB();
    }

    public void mapInstanceToNewInstanceId(SATDDifference diff) throws SQLException {
        int projectID = mappingDB.checkProjectID(diff.getProjectName(), diff.getProjectURI());
        String oldCommitId = mappingDB.getCommitId(new CommitMetaData(diff.getOldCommit()), projectID, false);
        String newCommitId = mappingDB.getCommitId(new CommitMetaData(diff.getNewCommit()), projectID, false);
        //並列化がうまく行っているか確認するため，
        System.out.println("oldCommitId" + oldCommitId);
        System.out.println("newCommitId" + newCommitId);

    }
}


