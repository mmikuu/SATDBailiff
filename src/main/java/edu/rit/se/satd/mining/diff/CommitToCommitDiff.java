package edu.rit.se.satd.mining.diff;

import edu.rit.se.git.GitUtil;
import edu.rit.se.git.RepositoryCommitReference;
import edu.rit.se.satd.comment.model.GroupedComment;
import edu.rit.se.satd.detector.SATDDetector;
import edu.rit.se.satd.model.SATDInstance;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffAlgorithm;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.revwalk.RevCommit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class CommitToCommitDiff {

    private final Git gitInstance;
    private final RevCommit newCommit;
    private final List<DiffEntry> diffEntries;
    private final SATDDetector detector;

    private String aimDir;

    public static DiffAlgorithm diffAlgo = DiffAlgorithm.getAlgorithm(DiffAlgorithm.SupportedAlgorithm.MYERS);

    public CommitToCommitDiff(RepositoryCommitReference oldRepo,
                              RepositoryCommitReference newRepo, SATDDetector detector,String aimDir) {
        this.gitInstance = newRepo.getGitInstance();
        this.newCommit = newRepo.getCommit();
        this.aimDir = aimDir;
        this.diffEntries = GitUtil.getDiffEntries(this.gitInstance, oldRepo.getCommit(), this.newCommit)
                .stream()
                .filter(diffEntry -> isRelevantFile(diffEntry))
                .collect(Collectors.toList());
        this.detector = detector;
    }

    public boolean isRelevantFile(DiffEntry diffEntry){
        return isJavaFile(diffEntry.getOldPath()) ||
                isJavaFile(diffEntry.getNewPath()) ||
                isCFile(diffEntry.getOldPath()) ||
                isCFile(diffEntry.getNewPath()) ||
                isEcmaFile(diffEntry.getOldPath()) ||
                isEcmaFile(diffEntry.getNewPath()) ||
                isPyFile(diffEntry.getOldPath()) ||
                isPyFile(diffEntry.getNewPath()) ||
                isPhpFile(diffEntry.getOldPath()) ||
                isPhpFile(diffEntry.getNewPath()) ||
                isRubyFile(diffEntry.getOldPath()) ||
                isRubyFile(diffEntry.getNewPath()) ||
                isCsharpFile(diffEntry.getOldPath()) ||
                isCsharpFile(diffEntry.getNewPath());
    }

    private boolean isJavaFile(String path) {
        return path != null && path.endsWith(".java");
    }

    private boolean isPyFile(String path) {
        return path != null && path.endsWith(".py");
    }

    private boolean isPhpFile(String path) {
        return path != null && path.endsWith(".php");
    }

    private boolean isCsharpFile(String path) {
        return path != null && path.endsWith(".cs");
    }

    private boolean isRubyFile(String path) {
        return path != null && path.endsWith(".rb");
    }

    private boolean isCFile(String path) {
        if (path == null) {
            return false;
        }

        String[] cppExtensions = {".c", ".cc", ".cp", ".cpp", ".cx", ".cxx", ".c+", ".c++",
                ".h", ".hh", ".hxx", ".h+", ".h++", ".hp", ".hpp"};

        for (String extension : cppExtensions) {
            if (path.endsWith(extension)) {
                return true;
            }
        }
        return false;
    }

    private boolean isEcmaFile(String path) {
        return path != null && path.endsWith(".js");
    }

    public List<String> getModifiedFilesNew() {
        return this.diffEntries.stream()
                .map(DiffEntry::getNewPath)
                .collect(Collectors.toList());
    }

    public List<String> getModifiedFilesOld() {
        return this.diffEntries.stream()
                .map(DiffEntry::getOldPath)
                .collect(Collectors.toList());
    }

    public List<SATDInstance> loadDiffsForOldFile(String oldFile, GroupedComment comment) {
        return this.loadDiffsForFile(oldFile, comment,
                new OldFileDifferencer(this.gitInstance, this.newCommit, this.detector, this.diffEntries,this.aimDir));

    }

    public List<SATDInstance> loadDiffsForNewFile(String newFile, GroupedComment comment) {
        return this.loadDiffsForFile(newFile, comment, new NewFileDifferencer(this.gitInstance));
    }

    private List<SATDInstance> loadDiffsForFile(String file, GroupedComment comment, FileDifferencer differ) {
        return this.diffEntries.stream()
                .filter(entry -> differ.getPertinentFilePath(entry).equals(file))
                .map(diffEntry -> differ.getInstancesFromFile(diffEntry, comment))
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

}
