package com.analysis.tool.testgen;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Phase 8B / task G2: decides which methods of the target project the test
 * generator can handle in version 1, and records a reason for every method it
 * cannot. Pure source analysis (JavaParser); nothing is compiled or executed.
 *
 * Version-1 rules (see docs/TEST_GENERATION_SPEC.md):
 * - only top-level, public, concrete classes (interfaces and abstract classes
 *   are reported as skipped);
 * - public, non-generic methods with a body, a supported non-void return type
 *   and only supported parameter types;
 * - methods whose body mentions time, randomness, I/O or threads are skipped;
 * - instance methods need a constructible receiver: a class with no explicit
 *   constructor, or a public constructor whose parameters are supported types
 *   or other constructible project classes (depth-limited, cycle-safe).
 *
 * Limitation: project classes are looked up by simple name, consistent with the
 * rest of the tool. Two classes with the same simple name in different packages
 * are not distinguished (the first one wins).
 */
public class TargetDiscoverer {

    public static final int MAX_CONSTRUCTION_DEPTH = 3;

    static final Set<String> SUPPORTED_TYPES = Set.of(
            "int", "long", "short", "byte", "char", "boolean", "double", "float", "String",
            "Integer", "Long", "Short", "Byte", "Character", "Boolean", "Double", "Float");

    private static final List<String> NONDETERMINISM_TOKENS = List.of(
            "System.currentTimeMillis", "System.nanoTime", "Math.random", "new Random",
            "LocalDate.now", "LocalDateTime.now", "Instant.now", "UUID.randomUUID",
            "Thread.", "new File", "Files.", "Scanner", "System.in");

    private static final class ParsedClass {
        final String packageName;
        final ClassOrInterfaceDeclaration declaration;

        ParsedClass(String packageName, ClassOrInterfaceDeclaration declaration) {
            this.packageName = packageName;
            this.declaration = declaration;
        }
    }

    /**
     * Classifies every candidate method in the given source files. Output order
     * is deterministic: source order, then declaration order.
     */
    public List<DiscoveredMethod> discover(List<String> javaSources) {
        Map<String, ParsedClass> classes = new LinkedHashMap<>();
        for (String source : javaSources) {
            CompilationUnit unit = StaticJavaParser.parse(source);
            String packageName = unit.getPackageDeclaration()
                    .map(p -> p.getNameAsString()).orElse("");
            for (TypeDeclaration<?> type : unit.getTypes()) {
                if (type instanceof ClassOrInterfaceDeclaration) {
                    classes.putIfAbsent(type.getNameAsString(),
                            new ParsedClass(packageName, (ClassOrInterfaceDeclaration) type));
                }
            }
        }

        List<DiscoveredMethod> result = new ArrayList<>();
        for (Map.Entry<String, ParsedClass> entry : classes.entrySet()) {
            String className = entry.getKey();
            ParsedClass parsed = entry.getValue();
            ClassOrInterfaceDeclaration decl = parsed.declaration;

            for (MethodDeclaration method : decl.getMethods()) {
                boolean candidate = decl.isInterface() || method.isPublic();
                if (!candidate) {
                    continue;
                }
                result.add(classify(parsed.packageName, className, decl, method, classes));
            }
        }
        return result;
    }

    private DiscoveredMethod classify(String packageName, String className,
                                      ClassOrInterfaceDeclaration decl, MethodDeclaration method,
                                      Map<String, ParsedClass> classes) {
        String signature = signatureOf(method);
        boolean isStatic = method.isStatic();
        String name = method.getNameAsString();

        String reason = skipReason(className, decl, method, classes);
        return reason == null
                ? DiscoveredMethod.supported(packageName, className, name, signature, isStatic)
                : DiscoveredMethod.skipped(packageName, className, name, signature, isStatic, reason);
    }

    /** Returns the first reason the method is unsupported, or null if it is supported. */
    private String skipReason(String className, ClassOrInterfaceDeclaration decl, MethodDeclaration method,
                              Map<String, ParsedClass> classes) {
        if (decl.isInterface()) {
            return "declared in an interface";
        }
        if (!decl.isPublic()) {
            return "class is not public";
        }
        if (decl.isAbstract()) {
            return "class is abstract";
        }
        if (method.isAbstract() || method.getBody().isEmpty()) {
            return "method has no body";
        }
        if (!method.getTypeParameters().isEmpty()) {
            return "generic method";
        }
        if (method.getType().isVoidType()) {
            return "void return: no observable result in version 1";
        }
        String returnType = method.getType().asString();
        if (!SUPPORTED_TYPES.contains(returnType)) {
            return "unsupported return type: " + returnType;
        }
        for (Parameter parameter : method.getParameters()) {
            String type = parameter.getType().asString();
            if (!SUPPORTED_TYPES.contains(type)) {
                return "unsupported parameter type: " + type;
            }
        }
        String body = method.getBody().get().toString();
        for (String token : NONDETERMINISM_TOKENS) {
            if (body.contains(token)) {
                return "uses time, randomness, I/O or threads: " + token;
            }
        }
        if (!method.isStatic() && !isConstructible(className, 0, new HashSet<>(), classes)) {
            return "receiver not constructible: " + className;
        }
        return null;
    }

    private boolean isConstructible(String className, int depth, Set<String> visiting,
                                    Map<String, ParsedClass> classes) {
        ParsedClass parsed = classes.get(className);
        if (parsed == null || depth > MAX_CONSTRUCTION_DEPTH || !visiting.add(className)) {
            return false;
        }
        try {
            ClassOrInterfaceDeclaration decl = parsed.declaration;
            if (decl.isInterface() || decl.isAbstract() || !decl.isPublic()) {
                return false;
            }
            List<ConstructorDeclaration> constructors = decl.getConstructors();
            if (constructors.isEmpty()) {
                return true;
            }
            for (ConstructorDeclaration constructor : constructors) {
                if (constructor.isPublic() && parametersBuildable(constructor, depth, visiting, classes)) {
                    return true;
                }
            }
            return false;
        } finally {
            visiting.remove(className);
        }
    }

    private boolean parametersBuildable(ConstructorDeclaration constructor, int depth, Set<String> visiting,
                                        Map<String, ParsedClass> classes) {
        for (Parameter parameter : constructor.getParameters()) {
            String type = parameter.getType().asString();
            if (SUPPORTED_TYPES.contains(type)) {
                continue;
            }
            if (type.contains("<") || type.contains("[")) {
                return false;
            }
            if (!isConstructible(type, depth + 1, visiting, classes)) {
                return false;
            }
        }
        return true;
    }

    private static String signatureOf(MethodDeclaration method) {
        return method.getNameAsString() + "("
                + method.getParameters().stream()
                        .map(p -> p.getType().asString())
                        .collect(Collectors.joining(","))
                + ")";
    }
}
