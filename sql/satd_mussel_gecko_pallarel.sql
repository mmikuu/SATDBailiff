DROP TABLE IF EXISTS satd_gecko_pallarel.SATD, satd_gecko_pallarel.Commits, satd_gecko_pallarel.SATDInFile, satd_gecko_pallarel.Projects,satd_gecko_pallarel.WaitChange, satd_gecko_pallarel.WaitCommits, satd_gecko_pallarel.WaitSATD, satd_gecko_pallarel.WaitSATDInFile;

CREATE TABLE IF NOT EXISTS satd_gecko_pallarel.Projects (
	p_id INT AUTO_INCREMENT NOT NULL,
    p_name VARCHAR(255) NOT NULL UNIQUE,
    p_url VARCHAR(255) NOT NULL UNIQUE,
    PRIMARY KEY (p_id)
);

CREATE TABLE IF NOT EXISTS satd_gecko_pallarel.SATDInFile (
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

CREATE TABLE IF NOT EXISTS satd_gecko_pallarel.Commits(
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

CREATE TABLE IF NOT EXISTS satd_gecko_pallarel.SATD (
    satd_id INT AUTO_INCREMENT,
    satd_instance_id varchar(256), -- Not a key value, used only to associate SATD Instances
    parent_instance_id INT,
    p_id INT,
    first_commit varchar(256),
    second_commit varchar(256),
    first_file INT,
    second_file INT,
    resolution VARCHAR(64),
    hash_code INT,
    PRIMARY KEY (satd_id),
    FOREIGN KEY (p_id) REFERENCES satd_gecko_pallarel.Projects(p_id),
    FOREIGN KEY (p_id, first_commit) REFERENCES satd_gecko_pallarel.Commits(p_id, commit_hash),
    FOREIGN KEY (p_id, second_commit) REFERENCES satd_gecko_pallarel.Commits(p_id, commit_hash),
    FOREIGN KEY (first_file) REFERENCES satd_gecko_pallarel.SATDInFile(f_id),
    FOREIGN KEY (second_file) REFERENCES satd_gecko_pallarel.SATDInFile(f_id)
);

CREATE TABLE IF NOT EXISTS satd_gecko_pallarel.SummaryFSATD (
    satd_id INT AUTO_INCREMENT,
    p_id INT,
    p_name VARCHAR(255) NOT NULL,
    p_url VARCHAR(255) NOT NULL,
    first_commit varchar(256),
    second_commit varchar(256),
    author_date DATETIME,
    committer_name varchar(256),
    resolution VARCHAR(64),
    f_comment VARCHAR(4096),
    f_comment_type VARCHAR(32),
    f_path VARCHAR(512),
    start_line INT,
    end_line INT,
    PRIMARY KEY (satd_id)
);


CREATE TABLE IF NOT EXISTS satd_gecko_pallarel.WaitChange (
    wait_id INT AUTO_INCREMENT,
    parent_hashcode INT,
    new_hash_code INT,
    resolution varchar(64),
    newCommitId varchar(256),
    oldCommitId varchar(256),
    newFileId INT,
    oldFileId INT,
    p_id INT,
    PRIMARY KEY (wait_id)
    );


CREATE TABLE IF NOT EXISTS satd_gecko_pallarel.WaitSATDInFile (
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


CREATE TABLE IF NOT EXISTS satd_gecko_pallarel.WaitCommits(
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

CREATE TABLE IF NOT EXISTS satd_gecko_pallarel.WaitSATD (
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

