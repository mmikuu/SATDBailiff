package edu.rit.se.util;

import com.github.javaparser.TokenRange;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.visitor.GenericVisitor;
import com.github.javaparser.ast.visitor.VoidVisitor;

public class JavaBlockComment extends Comment{

    public JavaBlockComment(TokenRange tokenRange, String content) {
        super(tokenRange, content);
    }


    @Override
    public <R, A> R accept(GenericVisitor<R, A> v, A arg) {
        return null;
    }

    @Override
    public <A> void accept(VoidVisitor<A> v, A arg) {

    }
}
