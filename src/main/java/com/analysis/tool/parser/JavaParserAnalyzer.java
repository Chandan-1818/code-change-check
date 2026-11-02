package com.analysis.tool.parser;

import java.util.ArrayList;
import java.util.List;

import com.analysis.tool.graph.MemberId;
import com.github.javaparser.ParseProblemException;
import com.github.javaparser.ParserConfiguration.LanguageLevel;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.comments.Comment;

/**
 * Parses Java source and extracts method declarations.
 *
 * Comments are removed before extraction, so comment-only edits are not
 * reported as method changes. Whitespace is already normalised because
 * body text comes from JavaParser's pretty printer, not the raw source.
 *
 * The parser is configured for the Java 17 language level so records,
 * text blocks and switch expressions are accepted (GAP-12). A source that
 * still cannot be parsed raises a typed SourceParseException instead of
 * JavaParser's raw ParseProblemException.
 */
public class JavaParserAnalyzer {

    static {
        StaticJavaParser.getConfiguration().setLanguageLevel(LanguageLevel.JAVA_17);
    }

    /** Parses source and strips every comment from the tree. */
    public static CompilationUnit parseWithoutComments(String javaSourceCode) {
        CompilationUnit cu;
        try {
            cu = StaticJavaParser.parse(javaSourceCode);
        } catch (ParseProblemException e) {
            throw new SourceParseException("Failed to parse Java source: " + e.getMessage(), e);
        }
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
            methods.add(new MethodInfo(
                    enclosingTypeName(declaration, "UNKNOWN"),
                    declaration.getNameAsString(),
                    bodyText,
                    header,
                    declaration.getParameters().size(),
                    hasVarargs(declaration.getParameters())));
        }
        return methods;
    }

    /**
     * Extracts constructor declarations. The member name is the sentinel
     * "&lt;init&gt;" (see MemberId.CONSTRUCTOR_NAME) so a constructor can never
     * be confused with a method; className is the declaring class.
     */
    public List<MethodInfo> extractConstructors(String javaSourceCode) {
        List<MethodInfo> constructors = new ArrayList<>();
        for (ConstructorDeclaration declaration : parseWithoutComments(javaSourceCode)
                .findAll(ConstructorDeclaration.class)) {
            String bodyText = declaration.getBody().toString();
            String header = declaration.getAnnotations().toString()
                    + " " + declaration.getModifiers().toString()
                    + declaration.getNameAsString() + declaration.getParameters();
            constructors.add(new MethodInfo(
                    enclosingTypeName(declaration, declaration.getNameAsString()),
                    MemberId.CONSTRUCTOR_NAME,
                    bodyText,
                    header,
                    declaration.getParameters().size(),
                    hasVarargs(declaration.getParameters())));
        }
        return constructors;
    }

    private static String enclosingTypeName(Node node, String fallback) {
        return node.findAncestor(TypeDeclaration.class)
                .map(TypeDeclaration::getNameAsString)
                .orElse(fallback);
    }

    private static boolean hasVarargs(List<Parameter> parameters) {
        for (Parameter parameter : parameters) {
            if (parameter.isVarArgs()) {
                return true;
            }
        }
        return false;
    }
}
