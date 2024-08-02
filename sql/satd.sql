DROP TABLE IF EXISTS satd.Commits, satd.SATD, satd.SATDInFile, satd.Projects, satd.WaitChange, satd.WaitCommits, satd.WaitSATD, satd.WaitSATDInFile;

CREATE TABLE IF NOT EXISTS satd.Projects (
	p_id INT AUTO_INCREMENT NOT NULL,
    p_name VARCHAR(255) NOT NULL UNIQUE,
    p_url VARCHAR(255) NOT NULL UNIQUE,
    PRIMARY KEY (p_id)
);

CREATE TABLE IF NOT EXISTS satd.SATDInFile (
    f_id INT AUTO_INCREMENT NOT NULL,
    f_comment VARCHAR(4096),
    f_comment_type VARCHAR(32),
    f_path VARCHAR(512),
    start_line INT,
    end_line INT,
    containing_class VARCHAR(512),
    containing_method VARCHAR(512),
    hash_code INT,
    PRIMARY KEY (f_id)
);

CREATE TABLE IF NOT EXISTS satd.Commits(
	commit_hash varchar(256),
    p_id INT,
    author_name varchar(256),
    author_email varchar(256),
    author_date DATETIME,
    committer_name varchar(256),
    committer_email varchar(256),
    commit_date DATETIME,
    PRIMARY KEY (p_id, commit_hash),
    FOREIGN KEY (p_id) REFERENCES Projects(p_id)
);

CREATE TABLE IF NOT EXISTS satd.SATD (
	satd_id INT AUTO_INCREMENT,
    satd_instance_id varchar(256), -- Not a key value, used only to associate SATD Instances
    parent_instance_id INT,
    p_id INT,
	first_commit varchar(256),
    second_commit varchar(256),
    first_file INT,
    second_file INT,
    resolution VARCHAR(64),
    hash_code varchar(256),
    PRIMARY KEY (satd_id),
    FOREIGN KEY (p_id) REFERENCES satd.Projects(p_id),
    FOREIGN KEY (p_id, first_commit) REFERENCES satd.Commits(p_id, commit_hash),
    FOREIGN KEY (p_id, second_commit) REFERENCES satd.Commits(p_id, commit_hash),
    FOREIGN KEY (first_file) REFERENCES satd.SATDInFile(f_id),
    FOREIGN KEY (second_file) REFERENCES satd.SATDInFile(f_id)
);

CREATE TABLE IF NOT EXISTS satd.WaitChange (
    wait_id INT AUTO_INCREMENT,
    parent_hashcode INT,
    resolution varchar(64),
    newCommitId varchar(256),
    oldCommitId varchar(256),
    newFileId INT,
    oldFileId INT,
    p_id INT,
    PRIMARY KEY (wait_id)
    );


CREATE TABLE IF NOT EXISTS satd.WaitSATDInFile (
    f_id INT AUTO_INCREMENT NOT NULL,
    f_comment VARCHAR(4096),
    f_comment_type VARCHAR(32),
    f_path VARCHAR(512),
    start_line INT,
    end_line INT,
    containing_class VARCHAR(512),
    containing_method VARCHAR(512),
    hash_code INT,
    PRIMARY KEY (f_id)
    );


CREATE TABLE IF NOT EXISTS satd.WaitCommits(
    commit_hash varchar(256),
    p_id INT,
    author_name varchar(256),
    author_email varchar(256),
    author_date DATETIME,
    committer_name varchar(256),
    committer_email varchar(256),
    commit_date DATETIME,
    PRIMARY KEY (p_id, commit_hash)
    );

CREATE TABLE IF NOT EXISTS satd.WaitSATD (
    satd_id INT AUTO_INCREMENT,
    parent_instance_id INT,
    p_id INT,
    first_commit varchar(256),
    second_commit varchar(256),
    first_file INT,
    second_file INT,
    resolution VARCHAR(64),
    hash_code varchar(256),
    PRIMARY KEY (satd_id)
    );