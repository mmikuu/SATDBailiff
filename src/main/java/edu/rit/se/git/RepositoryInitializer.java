package edu.rit.se.git;

import lombok.Getter;
import lombok.NonNull;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import java.io.File;
import java.io.IOException;

/**
 * Initializes a Git Repository. This includes cloning it locally and
 * locating unknown commits within it.
 */
public class RepositoryInitializer {

    // Git command constants
    private static final String REMOTE = "remote";
    private static final String ORIGIN = "origin";
    private static final String URL = "url";
    private static final String GIT_USERNAME = "u";
    private static final String GIT_PASSWORD = "p";

    // Program constants
    private static String REPO_OUT_DIR = "repos";

    // Constructor fields
    @Getter
    @NonNull
    private String repoDir;
    @NonNull
    private String gitURI;

    @NonNull
    private String gitUsername = GIT_USERNAME;
    @NonNull
    private String gitPassword = GIT_PASSWORD;

    // Set after initialization
    private Git repoRef = null;

    // Prevents other functionality of the class from being used if the git init fails
    private Boolean gitDidInit = false;

    public RepositoryInitializer(String uri, String baseName,String aimDir) {
        this.REPO_OUT_DIR = this.REPO_OUT_DIR+aimDir;
        this.repoDir = String.join(File.separator, REPO_OUT_DIR, baseName);
        this.gitURI = uri;

    }

    public RepositoryInitializer(String uri, String baseName, String gitUsername, String gitPassword,String aimDir) {
        this.REPO_OUT_DIR = this.REPO_OUT_DIR+aimDir;
        this.repoDir = String.join(File.separator, REPO_OUT_DIR, baseName);
        this.gitURI = uri;
        this.gitUsername = gitUsername;
        this.gitPassword = gitPassword;
    }

    /**
     * Initializes the repository, which:
     * 1. Clones the repository locally (Don't forget to clean it up)
     * 2. Sets the remote reference for the repository
     * @return True if the initialization was successful, else False
     */
    public boolean initRepo() throws IOException, GitAPIException {
        final File newGitRepo = new File(this.repoDir).getAbsoluteFile();

        if (newGitRepo.exists()) {
            // リポジトリが存在する場合、既存のリポジトリを開くようになっている
            openExistingRepository(newGitRepo);
        } else {
            // リポジトリが存在しない場合、クローンを実行
            cloneRepository(newGitRepo);
        }
        this.gitDidInit = true;
        return this.gitDidInit;
    }

    private void openExistingRepository(File repoDir) throws IOException {
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        Repository repository = builder.setGitDir(new File(repoDir, "/.git"))
                .readEnvironment()
                .findGitDir()
                .build();
//        System.out.println("Opened existing repository at: " + repository.getDirectory());
        this.repoRef = new Git(repository);
        System.out.println("open at this.repoRef"+this.repoRef.hashCode());
    }

    private void cloneRepository(File repoDir) throws GitAPIException, IOException {
        repoDir.mkdirs();
        this.repoRef = Git.cloneRepository()
                .setCredentialsProvider(new UsernamePasswordCredentialsProvider(this.gitUsername, this.gitPassword))
                .setURI(this.gitURI)
                .setDirectory(repoDir)
                .setCloneAllBranches(true)
                .call();
        // リモートインスタンスをリポジトリに追加（タグのリスト表示に使用）
//        System.out.println("Cloned repository to: " + repoDir.getAbsolutePath());
        this.repoRef.getRepository().getConfig().setString(REMOTE, ORIGIN, URL, this.gitURI);
        this.repoRef.getRepository().getConfig().save();
        System.out.println("Cloned at this.repoRef"+this.repoRef.hashCode());
    }
    /**
     * Gets a diff reference for the most recent diff or the one at the given head
     * @param head a string representing a hash or tag to use as a head
     * @return A reference to the most recent diff or the one at the given head
     */
    public RepositoryCommitReference getMostRecentCommit(String head) {
        final RevWalk revWalk = new RevWalk(this.repoRef.getRepository());
        try {
            return new RepositoryCommitReference(
                    this.repoRef,
                    GitUtil.getRepoNameFromGithubURI(this.gitURI),
                    this.gitURI,
                    revWalk.parseCommit(this.repoRef.getRepository().resolve(
                            head != null ? head : Constants.HEAD))
            );
        } catch (IOException e) {
            System.err.println("\nCould not parse the supplied diff for the repository: " + head);
        }
        return null;
    }

    /**
     * Attempts to delete the files generated by the initializer
     */
    public void cleanRepo() {
        if( this.repoRef != null ) {
            this.repoRef.getRepository().close();
        }
        File repo = new File(this.repoDir);
        try {
            FileUtils.deleteDirectory(repo);
        } catch (IOException e) {
            System.err.println("\nError deleting git repo");
        }
    }

    public boolean didInitialize() {
        return this.gitDidInit;
    }
}
