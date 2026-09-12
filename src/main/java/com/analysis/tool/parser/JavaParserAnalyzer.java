package com.analysis.tool.parser;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JavaParserAnalyzer parses Java source code and extracts structural
 * information such as method declarations.
 *
 * Supported operations (current scope):
 * - Parsing a Java source string into an AST (CompilationUnit)
 * - Extracting method names declared in that source
 * - Extracting full MethodInfo (name + body text) for each method
 *
 * Not yet supported (future phases):
 * - Field extraction, method call extraction, inheritance/interfaces
 * - Comparing two versions of a file at the AST level (this belongs
 *   to a future ImpactAnalyzer/comparison component, not this class)
 */
public class JavaParserAnalyzer {

    /**
     * Parses the given Java source code and returns the names of all
     * methods declared in it (across all classes/interfaces in the file).
     */
    public List<String> extractMethodNames(String javaSourceCode) {
        List<String> methodNames = new ArrayList<>();

        CompilationUnit compilationUnit = StaticJavaParser.parse(javaSourceCode);

        List<MethodDeclaration> methods = compilationUnit.findAll(MethodDeclaration.class);
        for (MethodDeclaration method : methods) {
            methodNames.add(method.getNameAsString());
        }

        return methodNames;
    }

    /**
     * Parses the given Java source code and returns a MethodInfo for each
     * method declared in it, containing the method's name and its body
     * text (as written in the source, including whitespace/formatting).
     *
     * If a method has no body (e.g. an abstract or interface method),
     * its bodyText will be an empty string.
     */
    public List<MethodInfo> extractMethods(String javaSourceCode) {
        List<MethodInfo> methods = new ArrayList<>();

        CompilationUnit compilationUnit = StaticJavaParser.parse(javaSourceCode);

        List<MethodDeclaration> declarations = compilationUnit.findAll(MethodDeclaration.class);
        for (MethodDeclaration declaration : declarations) {
            String name = declaration.getNameAsString();

            Optional<com.github.javaparser.ast.stmt.BlockStmt> body = declaration.getBody();
            String bodyText = body.map(Object::toString).orElse("");

            methods.add(new MethodInfo(name, bodyText));
        }

        return methods;
    }
}
