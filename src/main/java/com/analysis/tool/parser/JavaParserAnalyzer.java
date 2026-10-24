package com.analysis.tool.parser;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.comments.Comment;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses Java source and extracts method declarations.
 *
 * Comments are removed before extraction, so comment-only edits are not
 * reported as method changes. Whitespace is already normalised because
 * body text comes from JavaParser's pretty printer, not the raw source.
 */
public class JavaParserAnalyzer {

    /** Parses source and strips every comment from the tree. */
    public static CompilationUnit parseWithoutComments(String javaSourceCode) {
        CompilationUnit cu = StaticJavaParser.parse(javaSourceCode);
        for (Comment comment : cu.getAllContainedComments()) {
            comment.remove();
        }
        return cu;
    }

    public List<String> extractMethodNames(String javaSourceCode) {
        List<String> methodNames = new ArrayList<>();
        for (MethodDeclaration method : parseWithoutComments(javaSourceCode).findAll(MethodDeclaration.class)) {
            methodNames.add(method.getNameAsString());
        }
        return methodNames;
    }

    public List<MethodInfo> extractMethods(String javaSourceCode) {
        List<MethodInfo> methods = new ArrayList<>();
        for (MethodDeclaration declaration : parseWithoutComments(javaSourceCode).findAll(MethodDeclaration.class)) {
            String bodyText = declaration.getBody().map(Object::toString).orElse("");
            String header = declaration.getAnnotations().toString()
                    + " " + declaration.getDeclarationAsString(true, true, true);
            methods.add(new MethodInfo(declaration.getNameAsString(), bodyText, header));
        }
        return methods;
    }
}
