package edu.rit.se.satd.model;

import edu.rit.se.git.model.CommitMetaData;
import edu.rit.se.satd.comment.model.GroupedComment;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ScheduledThreadPoolExecutor;


public class SATDInstanceMappingDB {
    private String dbURI;
    private String user;
    private String pass;
    private static final int COMMENTS_MAX_CHARS = 4096;

    private final Map<String, Integer> cachedProjectKeys = new HashMap<>();
    private ScheduledThreadPoolExecutor finalWriteExecutor;

    private Connection conn;


    public SATDInstanceMappingDB() throws SQLException {
        //FIXME DBのプロパティMySQLOutputWritetrと同じように入れてみたけどどうなんや？
        this.dbURI = String.format("jdbc:mysql://%s:%s/%s?useSSL=%s",
                "127.0.0.1",
                "3306",
                "satd",
                "false");
        this.user = "root";
        this.pass = "your_password";
        final int maxConnections = Integer.parseInt( "151");
        this.finalWriteExecutor = new ScheduledThreadPoolExecutor( Math.max(1, maxConnections - 1));

        this.conn = DriverManager.getConnection(this.dbURI,this.user,this.pass);
    }


    public String getSATDInstanceId( SATDInstance satdInstance,
                                  String newCommitHash, String oldCommitHash,
                                  int newFileId, int oldFileId, int projectId,String instance_id ,int hash_code) throws SQLException{

        final PreparedStatement queryStmt = this.conn.prepareStatement(
                "SELECT SATD.satd_instance_id FROM SATD WHERE SATD.first_commit=? AND " +
                        "SATD.second_commit=? AND SATD.first_file=? AND SATD.second_file=?"
        );
        queryStmt.setString(1, oldCommitHash); // first_tag_id
        queryStmt.setString(2, newCommitHash); // second_tag_id
        queryStmt.setInt(3, oldFileId); // first_file
        queryStmt.setInt(4, newFileId); // second_file
        final ResultSet res = queryStmt.executeQuery();
        if( res.next() ) {
            // Return the result if one was found
            return res.getString(1);
        } else {
            UUID uuid = UUID.randomUUID();
            // Otherwise, add it and then return the newly generated key
            final PreparedStatement updateStmt = this.conn.prepareStatement(
                    "INSERT INTO SATD(first_commit, second_commit, first_file, second_file, " +
                            "resolution, satd_instance_id, p_id, parent_instance_id,hash_code) " +
                            "VALUES (?,?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            updateStmt.setString(1, oldCommitHash); // first_commit
            updateStmt.setString(2, newCommitHash); // second_commit
            updateStmt.setInt(3, oldFileId); // first_file
            updateStmt.setInt(4, newFileId); // second_file
            updateStmt.setString(5, satdInstance.getResolution().name());// resolution
            updateStmt.setString(6, String.valueOf(Objects.requireNonNullElse(instance_id,uuid))); // satd_instance_id
            updateStmt.setInt(7, projectId); // p_id
            updateStmt.setInt(8, satdInstance.getParentId()); // parent_instance_id
            updateStmt.setInt(9, hash_code); // parent_instance_id
            updateStmt.executeUpdate();
            final ResultSet updateRes = updateStmt.getGeneratedKeys();
            if (updateRes.next()) {
                return updateRes.getString(1);
            }
        }
        throw new SQLException("Could not obtain an SATD instance ID.");
    }


    public String getCommitId(CommitMetaData commitMetaData, int projectId , boolean isPending) throws SQLException {
        try {

            String tableName = isPending ? "WaitCommits" : "Commits";
            // Get CommitMetaData if not inserted already
            final PreparedStatement queryStmt = this.conn.prepareStatement(
                    "SELECT * FROM "+tableName+" WHERE commit_hash=?"
            );
            queryStmt.setString(1, commitMetaData.getHash()); // commit_hash
            final ResultSet res = queryStmt.executeQuery();
            if (!res.next()) {
                System.out.println("aaa");
                // Add the diff data if it is not found
                final PreparedStatement updateStmt = this.conn.prepareStatement(
                        "INSERT INTO "+tableName+"(commit_hash, author_name, author_email, " +
                                "committer_name, committer_email, author_date, commit_date, p_id) " +
                                "VALUES (?,?,?,?,?,?,?,?)");
                updateStmt.setString(1, commitMetaData.getHash()); // commit_hash
                updateStmt.setString(2, commitMetaData.getAuthorName()); // author_name
                updateStmt.setString(3, commitMetaData.getAuthorEmail()); // author_email
                updateStmt.setString(4, commitMetaData.getCommitterName()); // committer_name
                updateStmt.setString(5, commitMetaData.getCommitterEmail()); // committer_email
                if( commitMetaData.getAuthorDate() != null ) {
                    updateStmt.setTimestamp(6, new Timestamp(commitMetaData.getAuthorDate().getTime()), Calendar.getInstance()); // author_date
                } else {
                    updateStmt.setTimestamp(6, null);
                }
                if( commitMetaData.getCommitDate() != null ) {
                    updateStmt.setTimestamp(7, new Timestamp(commitMetaData.getCommitDate().getTime())); // commit_date
                } else {
                    updateStmt.setTimestamp(7, null);
                }
                updateStmt.setInt(8, projectId);
                updateStmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("SQL Error encountered when storing diff metadata.");
            throw e;
        }
        return commitMetaData.getHash();
    }

    public int getSATDInFileId(SATDInstance satdInstance, boolean useOld, int hashcode, boolean isPending) throws SQLException {
        // Get the correct values from the SATD Instance
        final String filePath = useOld ? satdInstance.getOldInstance().getFileName()
                : satdInstance.getNewInstance().getFileName();
        final int startLineNumber = useOld ? satdInstance.getStartLineNumberOldFile()
                : satdInstance.getStartLineNumberNewFile();
        final int endLineNumber = useOld ? satdInstance.getEndLineNumberOldFile()
                : satdInstance.getEndLineNumberNewFile();
        final GroupedComment comment = useOld ? satdInstance.getOldInstance().getComment() :
                satdInstance.getNewInstance().getComment();

        String tableName = isPending ? "WaitSATDInFile" : "SATDInFile";
        PreparedStatement queryStmt = this.conn.prepareStatement(
                "SELECT "+ tableName+".f_id FROM "+tableName+" WHERE " +
                        tableName+".f_comment=? AND "+tableName+".f_path=? AND " +
                        tableName+".start_line=? AND "+tableName+".end_line=?");

        queryStmt.setString(1, shortenStringToLength(
                comment.getComment().replace("\"", "\\\""), COMMENTS_MAX_CHARS)); // f_comment
        queryStmt.setString(2, filePath); // f_path
        queryStmt.setInt(3, startLineNumber); // start_line
        queryStmt.setInt(4, endLineNumber); // end_line

        final ResultSet res = queryStmt.executeQuery();

        if( res.next() ) {
            // Return the result if one was found
            return res.getInt(1);
        } else {
            String UpdateTableName = isPending ? "WaitSATDInFile" : "SATDInFile";
            // Otherwise, add it and then return the newly generated key
            final PreparedStatement updateStmt = this.conn.prepareStatement(
                    "INSERT INTO "+UpdateTableName+"(f_comment, f_comment_type, f_path, start_line, end_line, " +
                            "containing_class, containing_method,hash_code) " +
                            "VALUES (?,?,?,?,?,?,?,?);",
                    Statement.RETURN_GENERATED_KEYS);
            updateStmt.setString(1, shortenStringToLength(
                    comment.getComment().replace("\"", "\\\""), COMMENTS_MAX_CHARS)); // f_comment
            updateStmt.setString(2, comment.getCommentType()); // f_comment_type
            updateStmt.setString(3, filePath); // f_path
            updateStmt.setInt(4, startLineNumber); // start_line
            updateStmt.setInt(5, endLineNumber); // end_line
            updateStmt.setString(6, comment.getContainingClass());
            updateStmt.setString(7, comment.getContainingMethod());
            updateStmt.setInt(8, hashcode);
            updateStmt.executeUpdate();
            final ResultSet updateRes = updateStmt.getGeneratedKeys();
            if (updateRes.next()) {
                return updateRes.getInt(1);
            }
        }
        throw new SQLException("Could not obtain a file instance ID.");
    }


    public int getProjectId(String projectName, String projectUrl) throws SQLException {
        // Make query if Project exists
        final PreparedStatement queryStmt = this.conn.prepareStatement(
                "SELECT Projects.p_id FROM Projects WHERE Projects.p_name=?;");
        queryStmt.setString(1, projectName); // p_name
        final ResultSet res = queryStmt.executeQuery();
        if( res.next() ) {
            // Return the result if one was found
            return res.getInt(1);
        } else {
            // Otherwise, add it and then return the newly generated key
            final PreparedStatement updateStmt = this.conn.prepareStatement(
                    "INSERT INTO Projects(p_name, p_url) VALUES (?, ?);",
                    Statement.RETURN_GENERATED_KEYS);
            updateStmt.setString(1, projectName); // p_name
            updateStmt.setString(2, projectUrl); // p_url
            updateStmt.executeUpdate();
            final ResultSet updateRes = updateStmt.getGeneratedKeys();
            if (updateRes.next()) {
                return updateRes.getInt(1);
            }
        }
        // Some unpredicted issue was encountered, so just throw a new exception
        throw new SQLException("Could not obtain the project ID.");
    }


    public int checkProjectID(String projectName,String projectUrl) throws SQLException {
        int projectId;
        // Cache project key to shorten each write by one query
        if( this.cachedProjectKeys.containsKey(projectName) ) {
            projectId = this.cachedProjectKeys.get(projectName);
        } else {
            projectId = this.getProjectId(projectName, projectUrl);
            this.cachedProjectKeys.put(projectName, projectId);
        }
        return projectId;
    }


    public String checkInstanceID(int hashcode) throws SQLException {
        final PreparedStatement checkquery = this.conn.prepareStatement(
                "SELECT satd_instance_id\n" +
                        "FROM satd.SATD\n" +
                        "WHERE hash_code = ?;"
            );

        checkquery.setInt(1,hashcode);
        final ResultSet res = checkquery.executeQuery();
        if(res.next()){
            return res.getString("satd_instance_id");
        }else{
            return null;
        }
    }

    private static String shortenStringToLength(String str, int length) {
        return str.substring(0, Math.min(str.length(), length));
    }


    public int writeWaitChange(int parent_hashcode, String resolution, String newCommitId, String oldCommitId, int newFileId, int oldFileId, int p_id) throws SQLException {

        final PreparedStatement queryStmt = this.conn.prepareStatement(
                "SELECT WaitChange.wait_id FROM WaitChange WHERE WaitChange.parent_hashcode=? AND " +
                        "WaitChange.resolution=? AND WaitChange.newCommitId=? AND WaitChange.oldCommitId=?"
        );
        queryStmt.setInt(1, parent_hashcode); // first_tag_id
        queryStmt.setString(2, resolution); // second_tag_id
        queryStmt.setString(3, newCommitId); // first_file
        queryStmt.setString(4, oldCommitId); // second_file
        final ResultSet res = queryStmt.executeQuery();
        if( res.next() ) {
            // Return the result if one was found
            return res.getInt(1);
        } else {
            // Otherwise, add it and then return the newly generated key
            final PreparedStatement updateStmt = this.conn.prepareStatement(
                    "INSERT INTO WaitChange(parent_hashcode, resolution, newCommitId, oldCommitId, " +
                            "newFileId, oldFileId, p_id) " +
                            "VALUES (?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            updateStmt.setInt(1, parent_hashcode); // first_commit
            updateStmt.setString(2, resolution); // second_commit
            updateStmt.setString(3, newCommitId); // first_file
            updateStmt.setString(4, oldCommitId); // second_file
            updateStmt.setInt(5, newFileId);// resolution
            updateStmt.setInt(6, oldFileId); // satd_instance_id
            updateStmt.setInt(7, p_id); // p_id
            updateStmt.executeUpdate();
            final ResultSet updateRes = updateStmt.getGeneratedKeys();
            if (updateRes.next()) {
                return updateRes.getInt(1);
            }
        }
        throw new SQLException("Could not obtain an SATD instance ID.");
    }
    public void getWaitSATDInstanceId( SATDInstance satdInstance,
                                       String newCommitHash, String oldCommitHash,
                                       int newFileId, int oldFileId, int projectId ,int hash_code) throws SQLException{
        try{
            final PreparedStatement queryStmt = this.conn.prepareStatement(
                    "SELECT WaitSATD.satd_id FROM WaitSATD WHERE WaitSATD.first_commit=? AND " +
                            "WaitSATD.second_commit=? AND WaitSATD.first_file=? AND WaitSATD.second_file=?"
            );
            queryStmt.setString(1, oldCommitHash); // first_tag_id
            queryStmt.setString(2, newCommitHash); // second_tag_id
            queryStmt.setInt(3, oldFileId); // first_file
            queryStmt.setInt(4, newFileId); // second_file
            final ResultSet res = queryStmt.executeQuery();
            if( res.next() ) {
                System.out.println("あった");
                // Return the result if one was found
            } else {
                UUID uuid = UUID.randomUUID();
                // Otherwise, add it and then return the newly generated key
                final PreparedStatement updateStmt = this.conn.prepareStatement(
                        "INSERT INTO WaitSATD(first_commit, second_commit, first_file, second_file, " +
                                "resolution, p_id, parent_instance_id,hash_code) " +
                                "VALUES (?,?,?,?,?,?,?,?)",
                        Statement.RETURN_GENERATED_KEYS);
                updateStmt.setString(1, oldCommitHash); // first_commit
                updateStmt.setString(2, newCommitHash); // second_commit
                updateStmt.setInt(3, oldFileId); // first_file
                updateStmt.setInt(4, newFileId); // second_file
                updateStmt.setString(5, satdInstance.getResolution().name());// resolution
                updateStmt.setInt(6, projectId); // p_id
                updateStmt.setInt(7, -1); // parent_instance_id
                updateStmt.setInt(8, hash_code); // parent_instance_id
                updateStmt.executeUpdate();
                final ResultSet updateRes = updateStmt.getGeneratedKeys();
                if (updateRes.next()) {
                    System.out.println("新しく入れました");
                }
            }
        }catch (SQLException e){
            e.printStackTrace(); // 例外の詳細を出力
        }
//        throw new SQLException("Could not obtain an SATD instance ID.");
    }


}
