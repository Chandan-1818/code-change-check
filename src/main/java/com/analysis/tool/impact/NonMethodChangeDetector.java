package com.analysis.tool.impact;

import java.util.HashSet;
import java.util.Set;

import com.analysis.tool.parser.JavaParserAnalyzer;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.InitializerDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;

/**
 * Detects changes that method-level comparison cannot see: fields,
 * constants, imports, package, constructors, initialiser blocks and class
 * headers (annotations, extends, implements).
 *
 * Each file is reduced to a set of fingerprints of those non-method parts
 * (methods are deliberately excluded: MethodChangeDetector owns them, and
 * adding or removing a method must not look like a non-method change).
 *
 * A file is flagged only when something that EXISTED in the old version is
 * missing from the new version, i.e. it was changed or removed. Pure
 * additions (a new field, import, constructor) cannot alter the behaviour
 * of existing methods, so they are not flagged.
 *
 * Added or deleted files (a blank side) return false: every method is then
 * already classified as added or removed.
 */
public class NonMethodChangeDetector {

    public boolean hasNonMethodChange(String oldSource, String newSource) {
        if (oldSource == null || newSource == null
                || oldSource.trim().isEmpty() || newSource.trim().isEmpty()) {
            return false;
        }
        Set<String> oldPrints = fingerprints(oldSource);
        Set<String> newPrints = fingerprints(newSource);
        return !newPrints.containsAll(oldPrints);
    }

    private Set<String> fingerprints(String source) {
        CompilationUnit cu = JavaParserAnalyzer.parseWithoutComments(source);
        Set<String> prints = new HashSet<>();

        cu.getPackageDeclaration().ifPresent(p -> prints.add("P|" + p));
        for (ImportDeclaration imp : cu.getImports()) {
            prints.add("I|" + imp.toString().trim());
        }
        for (FieldDeclaration field : cu.findAll(FieldDeclaration.class)) {
            for (VariableDeclarator variable : field.getVariables()) {
                prints.add("F|" + field.getModifiers() + "|" + field.getAnnotations()
                        + "|" + variable.toString().trim());
            }
        }
        for (ConstructorDeclaration ctor : cu.findAll(ConstructorDeclaration.class)) {
            prints.add("C|" + ctor.toString().trim());
        }
        for (InitializerDeclaration init : cu.findAll(InitializerDeclaration.class)) {
            prints.add("N|" + init.toString().trim());
        }
        for (ClassOrInterfaceDeclaration type : cu.findAll(ClassOrInterfaceDeclaration.class)) {
            ClassOrInterfaceDeclaration header = type.clone();
            header.getMembers().clear();
            prints.add("T|" + header.toString().trim());
        }
        for (EnumDeclaration type : cu.findAll(EnumDeclaration.class)) {
            EnumDeclaration header = type.clone();
            header.getMembers().clear();
            prints.add("E|" + header.toString().trim());
        }
        for (RecordDeclaration type : cu.findAll(RecordDeclaration.class)) {
            RecordDeclaration header = type.clone();
            header.getMembers().clear();
            prints.add("R|" + header.toString().trim());
        }
        for (AnnotationDeclaration type : cu.findAll(AnnotationDeclaration.class)) {
            // The whole declaration is fingerprinted: an annotation type's
            // elements (e.g. "int value()") are not MethodDeclarations, so
            // MethodChangeDetector never sees them.
            prints.add("A|" + type.toString().trim());
        }
        return prints;
    }

    /**
     * True when the two versions differ structurally (after comments are
     * removed and whitespace is normalised by the parser's pretty printer),
     * even if the difference is only an addition.
     *
     * Unlike {@link #hasNonMethodChange}, which deliberately ignores pure
     * additions, this method answers the raw question "did this file change in
     * a way the member-level analysis has not explained?". It is used to turn a
     * changed Java file that no detector flagged into an uncertainty trigger
     * rather than silently ignoring it.
     *
     * A blank side (added or deleted file) returns false: every member is then
     * already classified as added or removed by the caller.
     */
    public boolean differsStructurally(String oldSource, String newSource) {
        if (isBlank(oldSource) || isBlank(newSource)) {
            return false;
        }
        return !normalised(oldSource).equals(normalised(newSource));
    }

    private static String normalised(String source) {
        return JavaParserAnalyzer.parseWithoutComments(source).toString();
    }

    private static boolean isBlank(String source) {
        return source == null || source.trim().isEmpty();
    }
}
