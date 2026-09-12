package com.analysis.tool.parser;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;

import java.util.ArrayList;
import java.util.List;

/**
 * JavaParserAnalyzer parses Java source code and extracts structural
 * information such as method declarations.
 *
 * Supported operations (current scope):
 * - Parsing a Java source string into an AST (CompilationUnit)
 * - Extracting method names declared in that source
 *
 * Not yet supported (future phases):
 * - Field extraction, method call extraction, inheritance/interfaces
 * - Comparing two versions of a file at the AST level
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
}
