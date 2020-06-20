package edu.rit.se.satd.mining.diff;

import edu.rit.se.git.GitUtil;
import edu.rit.se.satd.comment.model.GroupedComment;
import edu.rit.se.satd.comment.model.NullGroupedComment;
import edu.rit.se.satd.comment.model.RepositoryComments;
import edu.rit.se.satd.detector.SATDDetector;
import edu.rit.se.satd.model.SATDInstance;
import edu.rit.se.satd.model.SATDInstanceInFile;
import edu.rit.se.util.JavaParseUtil;
import edu.rit.se.util.KnownParserException;
import edu.rit.se.util.SimilarityUtil;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.TreeWalk;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static edu.rit.se.satd.comment.model.NullGroupedComment.NULL_FIELD;

public class OldFileDifferencer extends FileDifferencer {

    private RevCommit newCommit;

    private SATDDetector detector;

    OldFileDifferencer(Git gitInstance, RevCommit newCommit, SATDDetector detector) {
        super(gitInstance);
        this.newCommit = newCommit;
        this.detector = detector;
    }

    @Override
    public List<SATDInstance> getInstancesFromFile(DiffEntry diffEntry, GroupedComment oldComment) {
        final List<SATDInstance> satd = new ArrayList<>();

        switch (diffEntry.getChangeType()) {
            case RENAME:
                final RepositoryComments comInNewRepository =
                        this.getCommentsInFileInNewRepository(diffEntry.getNewPath());
                final GroupedComment newComment = comInNewRepository.getComments().stream()
                        .filter(nc -> nc.getComment().equals(oldComment.getComment()))
                        .filter(nc -> nc.getContainingMethod().equals(oldComment.getContainingMethod()))
                        .findFirst()
                        .orElse(new NullGroupedComment());
                // If the SATD couldn't be found in the new file, then it must have been removed
                final SATDInstance.SATDResolution resolution = ( newComment instanceof NullGroupedComment ) ?
                        SATDInstance.SATDResolution.SATD_REMOVED : SATDInstance.SATDResolution.FILE_PATH_CHANGED;
                satd.add(
                        new SATDInstance(
                                new SATDInstanceInFile(diffEntry.getOldPath(), oldComment),
                                new SATDInstanceInFile(diffEntry.getNewPath(), newComment),
                                resolution
                        ));
                break;
            case DELETE:
                satd.add(
                        new SATDInstance(
                                new SATDInstanceInFile(diffEntry.getOldPath(), oldComment),
                                new SATDInstanceInFile(diffEntry.getNewPath(), new NullGroupedComment()),
                                SATDInstance.SATDResolution.FILE_REMOVED
                        )
                );
                break;
            case MODIFY:
                // get the edits to the file, and the deletions to the SATD we're concerned about
                final List<Edit> editsToFile = this.getEdits(diffEntry);
                final List<Edit> editsToSATDComment = editsToFile.stream()
                        .filter(edit -> editImpactedComment(edit, oldComment, 0, true))
                        .collect(Collectors.toList());
                // Find the comments in the new repository version
                final RepositoryComments commentsInNewRepository =
                        this.getCommentsInFileInNewRepository(diffEntry.getNewPath());
                // Find the comments created by deleting
                final List<GroupedComment> updatedComments = editsToSATDComment.stream()
                        .flatMap( edit -> commentsInNewRepository.getComments().stream()
                                .filter( c -> editImpactedComment(edit, c, Math.max(0, oldComment.numLines() - c.numLines()), false)))
                        .collect(Collectors.toList());
                // If changes were made to the SATD comment, and now the comment is missing
                if( updatedComments.isEmpty() && !editsToSATDComment.isEmpty()) {
                    satd.add(
                            new SATDInstance(
                                    new SATDInstanceInFile(diffEntry.getOldPath(), oldComment),
                                    new SATDInstanceInFile(diffEntry.getNewPath(), new NullGroupedComment()),
                                    SATDInstance.SATDResolution.SATD_REMOVED
                            )
                    );
                }
                // If an updated comment was found, and it is not identical to the old comment
                if( !updatedComments.isEmpty() &&
                        updatedComments.stream()
                                .map(GroupedComment::getComment)
                                .noneMatch(oldComment.getComment()::equals)) {
                    satd.addAll(
                            updatedComments.stream()
                                    .map(nc -> {
                                        // If the comment that was added is similar enough to the old comment
                                        // we can infer that the comment was changed
                                        if( SimilarityUtil.commentsAreSimilar(oldComment, nc) ) {
                                            // If the new comment is still SATD, then the instance is changed
                                            if( this.detector.isSATD(nc.getComment()) ) {
                                                return new SATDInstance(
                                                        new SATDInstanceInFile(diffEntry.getOldPath(), oldComment),
                                                        new SATDInstanceInFile(diffEntry.getNewPath(), nc),
                                                        SATDInstance.SATDResolution.SATD_CHANGED);
                                            }
                                            // Otherwise the part of the comment that was making the comment SATD
                                            // was removed, and so it can be determined that the SATD instance
                                            // was removed
                                            else {
                                                return new SATDInstance(
                                                        new SATDInstanceInFile(diffEntry.getOldPath(), oldComment),
                                                        new SATDInstanceInFile(diffEntry.getNewPath(), nc),
                                                        SATDInstance.SATDResolution.SATD_REMOVED);
                                            }

                                        } else {
                                            // We know the comment was removed, and the one that was added
                                            // was a different comment
                                            return new SATDInstance(
                                                    new SATDInstanceInFile(diffEntry.getOldPath(), oldComment),
                                                    new SATDInstanceInFile(diffEntry.getNewPath(), new NullGroupedComment()),
                                                    SATDInstance.SATDResolution.SATD_REMOVED);
                                        }
                                    })
                                    .collect(Collectors.toList())
                    );
                }
                if(oldComment.getContainingMethod().equals(NULL_FIELD) ||
                        oldComment.getContainingClass().equals(NULL_FIELD) ||
                        editsTouchedClassOrMethodSignatureOldComment(editsToFile, oldComment)) {
                    // Check to see if the name of the containing method/class were updated
                    commentsInNewRepository.getComments().stream()
                            .filter(c -> c.getComment().equals(oldComment.getComment()))
                            .filter(c -> !c.getContainingClass().equals(oldComment.getContainingClass()) ||
                                    !c.getContainingMethod().equals(oldComment.getContainingMethod()))
                            // Determine if the comment's method or class was renamed
                            .filter(c -> editsToFile.stream().anyMatch( edit ->
                                    editImpactedContainingClass(edit, c, false) ||
                                            editImpactedContainingMethod(edit, c, false)))
                            .map(nc -> new SATDInstance(
                                    new SATDInstanceInFile(diffEntry.getOldPath(), oldComment),
                                    new SATDInstanceInFile(diffEntry.getNewPath(), nc),
                                    SATDInstance.SATDResolution.CLASS_OR_METHOD_CHANGED))
                            .findFirst()
                            .ifPresent(satd::add);
                    return satd;
                }
                break;
        }
        return satd;
    }


    private RepositoryComments getCommentsInFileInNewRepository(String fileName) {
        final RepositoryComments comments = new RepositoryComments();
        try {
            comments.addComments(JavaParseUtil.parseFileForComments(this.getFileContents(fileName), fileName));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (KnownParserException e) {
            comments.addParseErrorFile(e.getFileName());
        }
        return comments;
    }

    private InputStream getFileContents(String fileName) throws IOException {
        return this.gitInstance.getRepository().open(
                TreeWalk.forPath(this.gitInstance.getRepository(), fileName, this.newCommit.getTree()).getObjectId(0)
        ).openStream();
    }

    private boolean editImpactedComment(Edit edit, GroupedComment comment, int boundIncrease, boolean isOld) {
        return isOld ? GitUtil.editOccursInOldFileBetween(edit,
                comment.getStartLine() - boundIncrease, comment.getEndLine() + boundIncrease)
                : GitUtil.editOccursInNewFileBetween(edit,
                comment.getStartLine() - boundIncrease, comment.getEndLine() + boundIncrease);
    }

    private boolean editsTouchedClassOrMethodSignatureOldComment(List<Edit> edits, GroupedComment oldComment) {
        return edits.stream().anyMatch( edit ->
                editImpactedContainingClass(edit, oldComment, true) ||
                        editImpactedContainingMethod(edit, oldComment, true));
    }


    private boolean editImpactedContainingMethod(Edit edit, GroupedComment comment, boolean isOld) {
        return isOld ?
                GitUtil.editOccursInOldFileBetween(edit,
                        comment.getContainingMethodDeclarationLineStart(),
                        comment.getContainingMethodDeclarationLineEnd())
                : GitUtil.editOccursInNewFileBetween(edit,
                comment.getContainingMethodDeclarationLineStart(),
                comment.getContainingMethodDeclarationLineEnd());
    }

    private boolean editImpactedContainingClass(Edit edit, GroupedComment comment, boolean isOld) {
        return isOld ?
                GitUtil.editOccursInOldFileBetween(edit,
                        comment.getContainingClassDeclarationLineStart(),
                        comment.getContainingClassDeclarationLineEnd())
                : GitUtil.editOccursInNewFileBetween(edit,
                comment.getContainingClassDeclarationLineStart(),
                comment.getContainingClassDeclarationLineEnd());
    }
}