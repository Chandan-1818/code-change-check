package com.analysis.tool.graph;

import java.util.Collections;
import java.util.List;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt;

/**
 * Builds a DependencyGraph by parsing Java source code and recording,
 * for each method or constructor declaration, every call found inside its
 * body: ordinary method calls, object creations (new X(...)) and explicit
 * this(...) / super(...) constructor invocations.
 *
 * Current scope and limitation: without a configured Symbol Solver, the
 * exact declaring class of a called METHOD (e.g. what type "multiplier" is
 * in "multiplier.multiply(...)") cannot be reliably resolved. The caller's
 * class is known precisely (from its enclosing declaration); the callee's
 * class is recorded as "UNKNOWN" and only the callee's method name is
 * reliable. This means method edges are matched by method name only,
 * consistent with this project's existing name-based approach (see
 * MethodChangeDetector, TestImpactMapper).
 *
 * Constructors are different: "new X(...)" names X syntactically, so a
 * constructor callee node is MethodNode(X, "&lt;init&gt;") with a RESOLVED
 * class. This lets a constructor change be matched precisely.
 *
 * Every edge records the call-site argument count, so overloads that differ
 * in arity can be told apart at the first impact hop.
 */
public class DependencyGraphBuilder {

    /**
     * Builds a graph from a single source file.
     */
    public DependencyGraph buildFromSource(String javaSourceCode) {
        return buildFromSources(Collections.singletonList(javaSourceCode));
    }

    /**
     * Builds a single merged graph from multiple source files (e.g. a
     * production file and its corresponding test file), so that call
     * chains spanning both files (test -> production -> production)
     * are captured as connected edges in one graph.
     */
    public DependencyGraph buildFromSources(List<String> javaSourceCodes) {
        DependencyGraph graph = new DependencyGraph();

        for (String javaSourceCode : javaSourceCodes) {
            CompilationUnit compilationUnit =
                    StaticJavaParser.parse(javaSourceCode);

            List<ClassOrInterfaceDeclaration> classes =
                    compilationUnit.findAll(ClassOrInterfaceDeclaration.class);

            for (ClassOrInterfaceDeclaration classDecl : classes) {
                String className = classDecl.getNameAsString();

                List<MethodDeclaration> methods =
                        classDecl.findAll(MethodDeclaration.class);

                for (MethodDeclaration method : methods) {
                    MethodNode callerNode =
                            new MethodNode(className, method.getNameAsString());
                    addCallEdges(graph, callerNode, className, method);
                }

                List<ConstructorDeclaration> constructors =
                        classDecl.findAll(ConstructorDeclaration.class);

                for (ConstructorDeclaration constructor : constructors) {
                    MethodNode callerNode =
                            new MethodNode(className, MemberId.CONSTRUCTOR_NAME);
                    addCallEdges(graph, callerNode, className, constructor);
                }
            }
        }

        return graph;
    }

    /**
     * Records every call found inside the given declaration body:
     * method calls (callee class UNKNOWN), object creations and explicit
     * constructor invocations (callee class resolved where possible).
     */
    private void addCallEdges(
            DependencyGraph graph,
            MethodNode callerNode,
            String enclosingClassName,
            Node declaration) {

        for (MethodCallExpr call : declaration.findAll(MethodCallExpr.class)) {
            MethodNode calleeNode =
                    new MethodNode("UNKNOWN", call.getNameAsString());
            graph.addEdge(callerNode, calleeNode, call.getArguments().size());
        }

        for (ObjectCreationExpr creation : declaration.findAll(ObjectCreationExpr.class)) {
            MethodNode calleeNode = new MethodNode(
                    creation.getType().getNameAsString(),
                    MemberId.CONSTRUCTOR_NAME);
            graph.addEdge(callerNode, calleeNode, creation.getArguments().size());
        }

        for (ExplicitConstructorInvocationStmt invocation
                : declaration.findAll(ExplicitConstructorInvocationStmt.class)) {
            String calleeClass = invocation.isThis() ? enclosingClassName : "UNKNOWN";
            MethodNode calleeNode =
                    new MethodNode(calleeClass, MemberId.CONSTRUCTOR_NAME);
            graph.addEdge(callerNode, calleeNode, invocation.getArguments().size());
        }
    }
}
