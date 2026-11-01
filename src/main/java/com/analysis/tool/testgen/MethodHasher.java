package com.analysis.tool.testgen;

import com.analysis.tool.parser.JavaParserAnalyzer;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Phase 8B / G6: SHA-256 fingerprint of every method of the top-level classes,
 * keyed by MethodKey (class, signature). Comments and formatting do not change
 * a hash (the parser strips comments and the pretty printer normalises layout);
 * annotations, modifiers, return type, parameters and body do.
 *
 * Limit: only the method's own text is hashed. A change in a callee is found by
 * impact analysis, not by this hash.
 */
public class MethodHasher {

    public Map<String, String> hash(List<String> sources) {
        Map<String, String> result = new TreeMap<>();
        for (String source : sources) {
            CompilationUnit unit = JavaParserAnalyzer.parseWithoutComments(source);
            for (TypeDeclaration<?> type : unit.getTypes()) {
                if (!(type instanceof ClassOrInterfaceDeclaration)) {
                    continue;
                }
                ClassOrInterfaceDeclaration decl = (ClassOrInterfaceDeclaration) type;
                for (MethodDeclaration method : decl.getMethods()) {
                    String signature = method.getNameAsString() + "("
                            + method.getParameters().stream()
                                    .map(p -> p.getType().asString())
                                    .reduce((a, b) -> a + "," + b).orElse("")
                            + ")";
                    String text = method.getAnnotations().toString() + " "
                            + method.getDeclarationAsString(true, true, true) + "\n"
                            + method.getBody().map(Object::toString).orElse("");
                    result.putIfAbsent(MethodKey.of(decl.getNameAsString(), signature), sha256(text));
                }
            }
        }
        return result;
    }

    static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
