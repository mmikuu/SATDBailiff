package edu.rit.se.util;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.javaparser.*;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.comments.CommentsCollection;
import edu.rit.se.satd.comment.IgnorableWords;
import edu.rit.se.satd.comment.model.GroupedComment;
import jp.naist.se.commentlister.FileAnalyzer;
import jp.naist.se.commentlister.reader.FileType;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import static edu.rit.se.satd.comment.model.GroupedComment.TYPE_COMMENTED_SOURCE;

public class JavaParseUtil {

    public static Range NULL_RANGE = new Range(new Position(-1, -1), new Position(-1, -1));

    /**
     * Gets a list of comments from the input java file
     * @param file An input stream containing the contents of a java file to parse for comments
     * @return a list of grouped comments that correlate to comments from the parsed java file
     */
    public static List<GroupedComment> parseFileForComments(InputStream file, String fileName) throws KnownParserException, IOException {
//        final JavaParser parser = new JavaParser();
//        final ParseResult parsedFile = parser.parse(file);
//        if( !parsedFile.getProblems().isEmpty() ) {
//            throw new KnownParserException(fileName);
//        }
//        final Iterator<GroupedComment> allComments = parsedFile.getCommentsCollection().isPresent() ?
//                ((CommentsCollection)parsedFile.getCommentsCollection().get())
//                        .getComments()
//                        .stream()
//                        .filter(comment -> !comment.isJavadocComment())
//                        .map(GroupedComment::fromJavaParserComment)
//                        .filter(comment -> !comment.getCommentType().equals(TYPE_COMMENTED_SOURCE))
//                        .filter(comment -> IgnorableWords.getIgnorableWords().stream()
//                                .noneMatch(word -> comment.getComment().contains(word)))
//                        .sorted()
//                        .iterator()
//                : Collections.emptyIterator();

//        while (allComments.hasNext()) {
//            GroupedComment comment = allComments.next();
//            System.out.println(comment.getComment());
//        }

        TreeSet<Comment> comments = new TreeSet<>((c1, c2) -> {
            int lineComparison = Integer.compare(c1.getBegin().get().line, c2.getBegin().get().line);
            return lineComparison != 0 ? lineComparison : c1.getContent().compareTo(c2.getContent());
        });
        jsonComment(fileName);
        readJson(comments,fileName);

        Iterator<GroupedComment> allComments = Collections.emptyIterator();
        if (!comments.isEmpty()) {
            allComments =
                    comments
                            .stream()
                            .filter(comment -> !comment.isJavadocComment())
                            .map(GroupedComment::fromJavaParserComment)
                            .filter(comment -> !comment.getCommentType().equals(TYPE_COMMENTED_SOURCE))
                            .filter(comment -> IgnorableWords.getIgnorableWords().stream()
                                    .noneMatch(word -> comment.getComment().contains(word)))
                            .sorted()
                            .iterator();
        }


//        while (allComments.hasNext()) {
//            System.out.println("================");
//            GroupedComment comment = allComments.next();
//            System.out.println(comment.getComment());
//        }



        final List<GroupedComment> groupedComments = new ArrayList<>();
        GroupedComment previousComment = null;
        while( allComments.hasNext() ) {

            final GroupedComment thisComment = allComments.next();
            if( previousComment != null && previousComment.precedesDirectly(thisComment) ) {
                previousComment = previousComment.joinWith(thisComment);
            } else {
                // Previous comment was the last of the group, so add it to the list

                if( previousComment != null ) {
                    groupedComments.add(previousComment);
                }
                // restart grouping with the current comment
                previousComment = thisComment;
//                System.out.println("========previ====");
//                System.out.println(previousComment);
            }
        }
        if( previousComment != null && !groupedComments.contains(previousComment) ) {
            groupedComments.add(previousComment);
        }

        return groupedComments;
    }

    /**
     * Determines if the given range occurred within the start and end bounds
     * @param range The range of the edit
     * @param start the starting bound
     * @param end the ending bound
     * @return True if the ranges overlap, else False
     */
    public static boolean isRangeBetweenBounds(Range range, int start, int end) {
        return Math.max(range.begin.line, start) <= Math.min(range.end.line, end);
    }

    public static void jsonComment(String fileName) {

        try (JsonGenerator gen = new JsonFactory().createGenerator(new FileOutputStream("testOutput.json"))) {

//            String f = "repos/mmikuu/CalcTestSatd/"+fileName;
//            String f = "repos/mmikuu/SampleTestForCheckSatd/"+fileName;
//            String f = "repos/satorukano/algorithm_template/"+fileName;
//            String f = "repos/eclipse-jdt/eclipse.jdt.core/"+fileName;
            String f = "repos/eclipse-platform/eclipse.platform.swt/"+fileName;
            // String f = "repos/eclipse-platform/eclipse.platform.ui/"+fileName;
//            String f = "repos/mozilla/gecko-dev/"+fileName;
            String fileSplit[] = fileName.split("/");
            fileName = fileSplit[fileSplit.length-1];

            File Af = new File(f);//filepathに変更する必要あり
            gen.writeStartObject();
            FileAnalyzer.extractComments(gen, Af.toPath(), fileName, FileType.getFileType(fileName));//java cpp 以外のファイルは解析されない

        }catch (IOException e) {
            throw new RuntimeException(e);
//            System.out.println("error");
        }
    }

    public static void readJson(TreeSet<Comment> comments,String fileName) throws IOException {
        try {


            String fileSplit[] = fileName.split("/");
            fileName = fileSplit[fileSplit.length-1];

            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(new File("testOutput.json")); // JSONファイルのパスを指定
            // Main.java.javaノードを取得
            JsonNode mainJavaNode = rootNode.path(fileName);

            // Textフィールドを持つノードを繰り返し処理
            Iterator<Map.Entry<String, JsonNode>> fields = mainJavaNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                JsonNode textNode = field.getValue().path("Text");
                JsonNode lineNode = field.getValue().path("Line");
                JsonNode charPositionInLineNode = field.getValue().path("CharPositionInLine");
                if (!textNode.isMissingNode() && !lineNode.isMissingNode() && !charPositionInLineNode.isMissingNode()) {
                    String text = textNode.asText();
                    int line = lineNode.asInt();
                    int charPositionInLine = charPositionInLineNode.asInt();

                    if (text.startsWith("//")) {
                        LineAddComments(text, line, charPositionInLine, comments);
                    } else if (text.startsWith("/*")) {
                        BlockAddComments(text, line, charPositionInLine, comments);
                    }
                    text = "#Hello world\n";
                    line = 2;
                    charPositionInLine = 5;

//                    if (text.startsWith("#")) {
//                        PythonAddComments(text, line, charPositionInLine, comments);
//                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private static void LineAddComments(String text,int line,int charPositionInLine,TreeSet<Comment> comments){
        String[] contexts = text.split("\n");
        int lineCount = 0;
        for (String context : contexts) {

            // Rangeオブジェクトの生成ブロックコメントとラインコメントを仕分ける今はブロックコメントしかとれてないからね
            Range range = Range.range(line+lineCount, charPositionInLine, line+lineCount + context.split("\n").length - 1,
                    text.contains("\n") ? text.lastIndexOf('\n') : charPositionInLine + text.length());

            // JavaTokenの生成
            JavaToken beginToken = new JavaToken(range, 0, context, null, null);
            JavaToken endToken = new JavaToken(range, 0, context, null, null);

            TokenRange tokenRange = new TokenRange(beginToken, endToken);
            comments.add(new JavaLineComment(tokenRange, context.replace("//","")));
            lineCount++;
        }
    }

    private static void BlockAddComments(String text, int line, int charPositionInLine, TreeSet<Comment> comments){
        // Rangeオブジェクトの生成ブロックコメントとラインコメントを仕分ける今はブロックコメントしかとれてないからね
        String[] countSpace = text.split("\n");
        Range range = Range.range(line, charPositionInLine, line + countSpace.length-1,
                text.contains("\n") ? text.lastIndexOf('\n') : charPositionInLine + text.length());

        // JavaTokenの生成
        JavaToken beginToken = new JavaToken(range, 0, text, null, null);
        JavaToken endToken = new JavaToken(range, 0, text, null, null);

        TokenRange tokenRange = new TokenRange(beginToken, endToken);
        comments.add(new JavaBlockComment(tokenRange, text.replace("/*","").replace("*/","").replace("*","")));

    }

}
