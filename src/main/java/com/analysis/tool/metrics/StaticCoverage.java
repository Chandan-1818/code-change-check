package com.analysis.tool.metrics;

import com.analysis.tool.graph.DependencyGraph;
import com.analysis.tool.graph.DependencyGraphBuilder;
import com.analysis.tool.graph.MethodNode;
import com.analysis.tool.impact.ImpactAnalyzer;
import com.analysis.tool.impact.ImpactSet;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Static method coverage: a production method is COVERED when reverse
 * reachability from it (the same traversal the selector uses) reaches a
 * method annotated @Test. This is NOT runtime coverage. Methods are matched
 * by simple name, so two methods with the same name share a verdict; this can
 * over-report coverage. Reported figures must be labelled "static reachability".
 */
public class StaticCoverage {

    public static final class Result {
        private final Set<String> production;
        private final Set<String> covered;

        Result(Set<String> production, Set<String> covered) {
            this.production = production;
            this.covered = covered;
        }

        public Set<String> getProduction() { return production; }
        public Set<String> getCovered() { return covered; }

        public Set<String> getUncovered() {
            Set<String> u = new TreeSet<>(production);
            u.removeAll(covered);
            return u;
        }

        /** Percentage, or NaN when there are no production methods. */
        public double percent() {
            return production.isEmpty() ? Double.NaN : 100.0 * covered.size() / production.size();
        }
    }

    public Result compute(List<String> productionSources, List<String> testSources) {
        Set<String> production = new TreeSet<>();
        for (String source : productionSources) {
            CompilationUnit unit = StaticJavaParser.parse(source);
            for (ClassOrInterfaceDeclaration type : unit.findAll(ClassOrInterfaceDeclaration.class)) {
                for (MethodDeclaration m : type.getMethods()) {
                    production.add(type.getNameAsString() + "." + m.getNameAsString());
                }
            }
        }

        Set<MethodNode> testNodes = new HashSet<>();
        for (String source : testSources) {
            CompilationUnit unit = StaticJavaParser.parse(source);
            for (ClassOrInterfaceDeclaration type : unit.findAll(ClassOrInterfaceDeclaration.class)) {
                for (MethodDeclaration m : type.getMethods()) {
                    if (m.isAnnotationPresent("Test")) {
                        testNodes.add(new MethodNode(type.getNameAsString(), m.getNameAsString()));
                    }
                }
            }
        }

        List<String> all = new ArrayList<>(productionSources);
        all.addAll(testSources);
        DependencyGraph graph = new DependencyGraphBuilder().buildFromSources(all);
        ImpactAnalyzer analyzer = new ImpactAnalyzer();

        Set<String> covered = new TreeSet<>();
        for (String key : production) {
            String methodName = key.substring(key.indexOf('.') + 1);
            ImpactSet impact = analyzer.analyzeImpact(graph, Collections.singleton(methodName));
            Set<MethodNode> reached = new HashSet<>(impact.getChangedMethods());
            reached.addAll(impact.getDirectlyImpacted());
            reached.addAll(impact.getTransitivelyImpacted().keySet());
            for (MethodNode node : reached) {
                if (testNodes.contains(node)) {
                    covered.add(key);
                    break;
                }
            }
        }
        return new Result(production, covered);
    }
}
