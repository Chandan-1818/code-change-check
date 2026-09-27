package com.analysis.tool.graph;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;

import java.util.List;

/**
 * Builds a DependencyGraph by parsing Java source code and recording,
 * for each method declaration, every method call found inside its body.
 *
 * Current scope and limitation: without a configured Symbol Solver,
 * the exact declaring class of a called method (e.g. what type
 * "multiplier" is in "multiplier.multiply(...)") cannot be reliably
 * resolved. The caller's class is known precisely (from its enclosing
 * declaration); the callee's class is recorded as "UNKNOWN" and only
 * the callee's method name is reliable. This means edges are matched
 * by method name only, consistent with this project's existing
 * name-based approach (see MethodChangeDetector, TestImpactMapper).
 */
public class DependencyGraphBuilder {

    public DependencyGraph buildFromSource(String javaSourceCode) {
        DependencyGraph graph = new DependencyGraph();

        CompilationUnit compilationUnit = StaticJavaParser.parse(javaSourceCode);

        List<ClassOrInterfaceDeclaration> classes =
                compilationUnit.findAll(ClassOrInterfaceDeclaration.class);

        for (ClassOrInterfaceDeclaration classDecl : classes) {
            String className = classDecl.getNameAsString();

            List<MethodDeclaration> methods = classDecl.findAll(MethodDeclaration.class);
            for (MethodDeclaration method : methods) {
                MethodNode callerNode = new MethodNode(className, method.getNameAsString());

                List<MethodCallExpr> calls = method.findAll(MethodCallExpr.class);
                for (MethodCallExpr call : calls) {
                    MethodNode calleeNode = new MethodNode("UNKNOWN", call.getNameAsString());
                    graph.addEdge(callerNode, calleeNode);
                }
            }
        }

        return graph;
    }
}
