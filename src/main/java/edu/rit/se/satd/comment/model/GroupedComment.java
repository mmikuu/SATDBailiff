package edu.rit.se.satd.comment.model;

import com.github.javaparser.Position;
import com.github.javaparser.Range;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import edu.rit.se.util.JavaParseUtil;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static edu.rit.se.util.JavaParseUtil.NULL_RANGE;

/**
 * A model used to represent a single comment
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE) // For internal use only
@AllArgsConstructor(access = AccessLevel.PROTECTED) // For internal use only
public class GroupedComment implements Comparable {

    private static final String UNKNOWN = "None";

    @Getter
    private int startLine = -1;
    @Getter
    private int endLine = -1;
    @Getter
    private String comment = UNKNOWN;
    @Getter
    private String commentType = UNKNOWN;
    @Getter
    private String containingClass = UNKNOWN;
    @Getter
    private int containingClassDeclarationLineStart = -1;
    @Getter
    private int containingClassDeclarationLineEnd = -1;
    @Getter
    private String containingMethod = UNKNOWN;
    @Getter
    private int containingMethodDeclarationLineStart = -1;
    @Getter
    private int containingMethodDeclarationLineEnd = -1;

    public static final String TYPE_COMMENTED_SOURCE = "CommentedSource";
    public static final String TYPE_BLOCK = "Block";
    public static final String TYPE_LINE = "Line";
    public static final String TYPE_ORPHAN = "Orphan";
    public static final String TYPE_JAVADOC = "JavaDoc";
    public static final String TYPE_UNKNOWN = "Unknown";


    public String toString(){
        return this.commentType+this.containingClass+this.containingClassDeclarationLineStart+this.containingClassDeclarationLineEnd+this.containingMethod+this.containingMethodDeclarationLineStart+this.containingMethodDeclarationLineEnd;
    }
    /**
     * Merges this comment with another comment
     * @param other another comment
     * @return a new GroupedComment instance that contains the combined
     * comments
     */
    public GroupedComment joinWith(GroupedComment other) {
        return new GroupedComment(
                Integer.min(this.startLine, other.startLine),
                Integer.max(this.endLine, other.endLine),
                this.startLine < other.startLine ?
                        String.join("\n", this.comment, other.comment) :
                        String.join("\n", other.comment, this.comment),
                this.commentType,
                this.containingClass,
                this.containingClassDeclarationLineStart,
                this.containingClassDeclarationLineEnd,
                this.containingMethod,
                this.containingMethodDeclarationLineStart,
                this.containingMethodDeclarationLineEnd);
    }

    /**
     * @param other another comment
     * @return True if this comment is the same type of comment, and appears directly before
     * the other comment. If this comment is a Commented source comment, then typing of the next comment is ignored
     */
    public boolean precedesDirectly(GroupedComment other) {
        return this.containingClass.equals(other.containingClass) &&
                (this.commentType.equals(other.commentType) || this.commentType.equals(TYPE_COMMENTED_SOURCE))  &&
                this.endLine + 1 == other.startLine;
    }

    public int numLines() {
        return 1 + this.endLine - this.startLine;
    }

    /**
     * Removes Java-syntax items from comment lines
     * @param commentLine a line of a java comment
     * @return A cleaned line
     */
    private static String cleanCommentLine(String commentLine) {
        final String newComment = commentLine.trim();
        if( newComment.startsWith("*") ) {
            return newComment.replaceFirst("\\*", "").trim();
        }
        return newComment;
    }

    @Override
    public int compareTo(Object o) {
        if( o instanceof GroupedComment ) {
            if( this.startLine > ((GroupedComment) o).startLine ) {
                return 1;
            } else if( this.startLine < ((GroupedComment) o).startLine ) {
                return -1;
            }
            return 0;
        }
        return -1;
    }

    @Override
    public boolean equals(Object obj) {
        if( obj instanceof  GroupedComment ) {
            return this.comment.equals(((GroupedComment) obj).getComment()) &&
                    this.startLine == ((GroupedComment) obj).startLine &&
                    this.endLine == ((GroupedComment) obj).endLine;
        }

        return super.equals(obj);
    }

    @Override
    public int hashCode() {
        return (this.comment + this.containingMethod + this.containingClass + this.commentType).hashCode();
    }

    public static GroupedComment fromJavaParserComment(Comment oldComment) {
        final GroupedComment newComment = new GroupedComment();
        // Line numbers
        if( oldComment.getRange().isPresent() ) {
            newComment.startLine = oldComment.getRange().get().begin.line;
            newComment.endLine = oldComment.getRange().get().end.line;
        }
        // Clean up and set comment
        newComment.comment = Arrays.stream(oldComment.getContent().trim().split("\n"))
                .map(GroupedComment::cleanCommentLine)
                .collect(Collectors.joining("\n"));
        newComment.commentType = newComment.comment.contains("{") || newComment.comment.contains(";") ? TYPE_COMMENTED_SOURCE
                : oldComment.isBlockComment() ? TYPE_BLOCK
                : oldComment.isLineComment() ? TYPE_LINE
                : oldComment.isOrphan() ? TYPE_ORPHAN
                : oldComment.isJavadocComment() ? TYPE_JAVADOC
                : TYPE_UNKNOWN;

        // Get containing class and method data if found
        final Optional<ClassOrInterfaceDeclaration> classRoot = oldComment.findRootNode()
                .findAll(ClassOrInterfaceDeclaration.class)
                .stream()
                .filter(dec -> dec.getRange().isPresent())
                .filter(dec ->
                        JavaParseUtil.isRangeBetweenBounds(
                                dec.getRange().get(), newComment.startLine, newComment.endLine))
                .findFirst();
        if( classRoot.isPresent() ) {
            // Class Data
            newComment.containingClass = classRoot
                    .map(ClassOrInterfaceDeclaration::getFullyQualifiedName)
                    .map(opt -> opt.orElse(UNKNOWN))
                    .get();
            final Range classRange = classRoot
                    .map(ClassOrInterfaceDeclaration::getName)
                    .map(Node::getRange)
                    .get()
                    .orElse(NULL_RANGE);
            newComment.containingClassDeclarationLineStart = classRange.begin.line;
            newComment.containingClassDeclarationLineEnd = classRange.end.line;

            // Method Data
            // We need to check for all comments between the end of the last comment
            // and the end of the current comment. This is done to associate comments that
            // are not inside the contents of a method with the method that they pertain to.

            // Get all class variables
            // TODO fix issue where class variables are defined after a method
            final List<VariableDeclarator> variables = classRoot.get().findAll(VariableDeclarator.class).stream()
                    .filter(varDec -> varDec.getParentNode().isPresent() &&
                            varDec.getParentNode().get().getParentNode().isPresent() &&
                            varDec.getParentNode().get().getParentNode().get() instanceof ClassOrInterfaceDeclaration)
                    .collect(Collectors.toList());
            final List<MethodDeclaration> allMethods = classRoot.get().findAll(MethodDeclaration.class).stream()
                    .filter(dec -> dec.getRange().isPresent())
                    .sorted(Comparator.comparingInt(x -> x.getRange().get().begin.line))
                    .collect(Collectors.toList());

            // The first method in the class can be assumed to begin after all class variables have
            // been defined, so we start our bounds for the first method we search at the end of the
            // final variable declaration
            int lastMethodEnd = variables.stream()
                    .filter(var -> var.getRange().isPresent())
                    .map(var -> var.getRange().get().end.line)
                    .max(Comparator.comparingInt(x -> x))
                    .orElse(-1);
            for (final MethodDeclaration curMethod : allMethods) {
                final Range methodRangeIncludingProceeding = new Range(
                        new Position(lastMethodEnd, 0),
                        new Position(curMethod.getRange().get().end.line, 0));
                // If the comment is within the bounds, then we can assume it related to this method
                if (JavaParseUtil.isRangeBetweenBounds(
                        methodRangeIncludingProceeding, newComment.startLine, newComment.endLine)) {
                    newComment.containingMethod = curMethod
                            .getDeclarationAsString(false, false, false);
                    newComment.containingMethod = newComment.containingMethod
                            .substring(newComment.containingMethod.indexOf(" ") + 1);
                    newComment.containingMethodDeclarationLineStart = curMethod.asMethodDeclaration()
                            .getRange().get().begin.line;
                    newComment.containingMethodDeclarationLineEnd = curMethod.asMethodDeclaration()
                            .getRange().get().end.line;
                    break;
                }
                lastMethodEnd = curMethod.getRange().get().end.line;
            }

        }
        return newComment;
    }

    public int getLine(){
        return this.startLine;
    }
}
