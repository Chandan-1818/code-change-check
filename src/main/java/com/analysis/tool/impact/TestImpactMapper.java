package com.analysis.tool.impact;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;

import java.util.HashSet;
import java.util.Set;

/**
 * Maps a changed method name to the set of test methods that exercise it,
 * by parsing raw test source code and searching for method call expressions.
 */
public class TestImpactMapper {

    /**
     * Given raw Java source of a test class and a changed method name,
     * returns the names of test methods whose body contains at least
     * one call to that method name.
     */
    public Set<String> findTestsExercisingMethod(String testSourceCode, String changedMethodName) {
        Set<String> impactedTests = new HashSet<>();

        CompilationUnit compilationUnit = StaticJavaParser.parse(testSourceCode);
        for (MethodDeclaration method : compilationUnit.findAll(MethodDeclaration.class)) {
            boolean callsChangedMethod = method.findAll(MethodCallExpr.class).stream()
                    .anyMatch(call -> call.getNameAsString().equals(changedMethodName));
            if (callsChangedMethod) {
                impactedTests.add(method.getNameAsString());
            }
        }

        return impactedTests;
    }
}
