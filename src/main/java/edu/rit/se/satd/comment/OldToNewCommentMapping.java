package edu.rit.se.satd.comment;


import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@RequiredArgsConstructor
public class OldToNewCommentMapping {

    @Getter
    final private GroupedComment comment;
    @Getter
    final private String file;

    @Getter
    private boolean isMapped = false;

    @Getter
    @Setter
    private int duplicationId = 0;

    public void mapTo(OldToNewCommentMapping other) {
        this.isMapped = true;
        if (other != null) {
            other.isMapped = true;
        }
    }

    public boolean isNotMapped() {
        return !this.isMapped;
    }

    public boolean commentsMatch(OldToNewCommentMapping other) {
        return this.comment.getComment().equals(other.comment.getComment())
                && this.comment.getContainingMethod().equals(other.comment.getContainingMethod())
                && this.comment.getContainingClass().equals(other.comment.getContainingClass())
                && this.file.equals(other.file)
                && this.duplicationId == other.duplicationId;
    }

    @Override
    public int hashCode() {
        return this.comment.hashCode() +
                this.file.hashCode() +
                this.duplicationId;
    }
}
