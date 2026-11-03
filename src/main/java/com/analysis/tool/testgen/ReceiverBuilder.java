package com.analysis.tool.testgen;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Phase 8B / task G3 (part 2): builds the Java expression that creates the
 * receiver object for an instance method, e.g.
 * "new OrderService(new OrderRepository())".
 *
 * Uses the same rules as TargetDiscoverer, so a class that discovery calls
 * constructible always gets an expression here: the first public constructor
 * (in declaration order) whose parameters can all be built, recursing into
 * other project classes up to TargetDiscoverer.MAX_CONSTRUCTION_DEPTH and
 * refusing cycles. Primitive, boxed and String arguments use
 * InputGenerator.safeConstructorValue.
 *
 * A constructor that throws for these arguments is not detected here; that
 * is found when the oracle is captured (task G4).
 */
public class ReceiverBuilder {

    private final Map<String, ClassOrInterfaceDeclaration> classes = new LinkedHashMap<>();

    public ReceiverBuilder(List<String> javaSources) {
        for (String source : javaSources) {
            CompilationUnit unit = StaticJavaParser.parse(source);
            for (TypeDeclaration<?> type : unit.getTypes()) {
                if (type instanceof ClassOrInterfaceDeclaration) {
                    classes.putIfAbsent(type.getNameAsString(), (ClassOrInterfaceDeclaration) type);
                }
            }
        }
    }

    /** Empty if the class cannot be constructed under the version-1 rules. */
    public Optional<String> build(String className) {
        return build(className, 0, new HashSet<>());
    }

    private Optional<String> build(String className, int depth, Set<String> visiting) {
        ClassOrInterfaceDeclaration decl = classes.get(className);
        if (decl == null || depth > TargetDiscoverer.MAX_CONSTRUCTION_DEPTH || !visiting.add(className)) {
            return Optional.empty();
        }
        try {
            if (decl.isInterface() || decl.isAbstract() || !decl.isPublic()) {
                return Optional.empty();
            }
            List<ConstructorDeclaration> constructors = decl.getConstructors();
            if (constructors.isEmpty()) {
                return Optional.of("new " + className + "()");
            }
            for (ConstructorDeclaration constructor : constructors) {
                if (!constructor.isPublic()) {
                    continue;
                }
                Optional<List<String>> arguments = buildArguments(constructor, depth, visiting);
                if (arguments.isPresent()) {
                    return Optional.of("new " + className + "(" + String.join(", ", arguments.get()) + ")");
                }
            }
            return Optional.empty();
        } finally {
            visiting.remove(className);
        }
    }

    private Optional<List<String>> buildArguments(ConstructorDeclaration constructor, int depth,
                                                  Set<String> visiting) {
        List<String> arguments = new ArrayList<>();
        for (Parameter parameter : constructor.getParameters()) {
            String type = parameter.getType().asString();
            if (TargetDiscoverer.SUPPORTED_TYPES.contains(type)) {
                arguments.add(InputGenerator.safeConstructorValue(type));
                continue;
            }
            if (type.contains("<") || type.contains("[")) {
                return Optional.empty();
            }
            Optional<String> nested = build(type, depth + 1, visiting);
            if (nested.isEmpty()) {
                return Optional.empty();
            }
            arguments.add(nested.get());
        }
        return Optional.of(arguments);
    }
}
