package com.analysis.tool.impact;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.analysis.tool.impact.ChangedFileClassifier.FileCategory;

/**
 * PHASE 14, scenarios 12-22: multi-language classification (categories A-H) of
 * changed files. Purely path/extension based (no Git, no parsing, no content
 * reads), so it is fast and portable, and it proves a non-Java file is never
 * classed as Java source.
 */
class ChangedFileClassifierCategoryTest {

    private final ChangedFileClassifier classifier = new ChangedFileClassifier();

    // Scenario 12 + 13: Java production (A) and Java test (B).
    @Test
    void javaProductionAndTestSourcesAreDistinguished() {
        assertEquals(FileCategory.JAVA_PRODUCTION,
                classifier.categorize("src/main/java/com/sample/Calculator.java"));
        assertEquals(FileCategory.JAVA_TEST,
                classifier.categorize("src/test/java/com/sample/CalculatorTest.java"));

        assertTrue(classifier.isJavaChange("src/main/java/com/sample/Calculator.java"));
        assertTrue(classifier.isJavaChange("src/test/java/com/sample/CalculatorTest.java"));
    }

    // Scenario 14: build files (C), including pom.xml and the Maven wrapper.
    @Test
    void buildFilesAreCategoryC() {
        assertEquals(FileCategory.BUILD, classifier.categorize("pom.xml"));
        assertEquals(FileCategory.BUILD, classifier.categorize("mvnw"));
        assertEquals(FileCategory.BUILD, classifier.categorize("mvnw.cmd"));
        assertEquals(FileCategory.BUILD,
                classifier.categorize(".mvn/wrapper/maven-wrapper.properties"));
        assertEquals(FileCategory.BUILD, classifier.categorize("build.gradle"));

        assertFalse(classifier.isJavaChange("pom.xml"));
    }

    // Scenario 15: configuration files (D).
    @Test
    void configurationFilesAreCategoryD() {
        assertEquals(FileCategory.CONFIGURATION, classifier.categorize("config/app.xml"));
        assertEquals(FileCategory.CONFIGURATION, classifier.categorize("app.properties"));
        assertEquals(FileCategory.CONFIGURATION, classifier.categorize("config.yml"));
        assertEquals(FileCategory.CONFIGURATION, classifier.categorize("config.yaml"));
        assertEquals(FileCategory.CONFIGURATION, classifier.categorize("package.json"));

        assertFalse(classifier.isJavaChange("config/app.xml"));
    }

    // Scenario 16: other programming languages (E) are detected but never Java.
    @Test
    void otherLanguagesAreCategoryEAndNeverJava() {
        List<String> paths = Arrays.asList(
                "scripts/tool.py", "web/app.js", "web/App.jsx", "web/app.ts", "web/App.tsx",
                "server/main.go", "native/engine.cpp", "native/buffer.c", "app/Program.cs",
                "web/index.php", "lib/tasks.rb");

        for (String path : paths) {
            assertEquals(FileCategory.OTHER_LANGUAGE, classifier.categorize(path),
                    "expected OTHER_LANGUAGE for " + path);
            assertFalse(classifier.isJavaChange(path), "must not be Java for " + path);
        }
    }

    // Scenario 17: documentation files (F), by extension and by known name.
    @Test
    void documentationIsCategoryF() {
        assertEquals(FileCategory.DOCUMENTATION, classifier.categorize("docs/guide.md"));
        assertEquals(FileCategory.DOCUMENTATION, classifier.categorize("notes.txt"));
        assertEquals(FileCategory.DOCUMENTATION, classifier.categorize("docs/spec.rst"));
        assertEquals(FileCategory.DOCUMENTATION, classifier.categorize("README"));
        assertEquals(FileCategory.DOCUMENTATION, classifier.categorize("LICENSE"));
        assertEquals(FileCategory.DOCUMENTATION, classifier.categorize(".gitignore"));

        assertFalse(classifier.isJavaChange("docs/guide.md"));
    }

    // Scenario 18: static assets (G).
    @Test
    void staticAssetsAreCategoryG() {
        assertEquals(FileCategory.STATIC_ASSET, classifier.categorize("web/index.html"));
        assertEquals(FileCategory.STATIC_ASSET, classifier.categorize("web/styles.css"));
        assertEquals(FileCategory.STATIC_ASSET, classifier.categorize("web/theme.scss"));
        assertEquals(FileCategory.STATIC_ASSET, classifier.categorize("web/logo.png"));
        assertEquals(FileCategory.STATIC_ASSET, classifier.categorize("web/font.woff2"));

        assertFalse(classifier.isJavaChange("web/index.html"));
    }

    // Scenario 19: binary / unknown files (H).
    @Test
    void binaryAndUnknownAreCategoryH() {
        assertEquals(FileCategory.BINARY_OR_UNKNOWN, classifier.categorize("lib/library.jar"));
        assertEquals(FileCategory.BINARY_OR_UNKNOWN, classifier.categorize("docs/report.pdf"));
        assertEquals(FileCategory.BINARY_OR_UNKNOWN, classifier.categorize("bin/tool.exe"));
        assertEquals(FileCategory.BINARY_OR_UNKNOWN, classifier.categorize("data/unknown.zzz"));
        assertEquals(FileCategory.BINARY_OR_UNKNOWN, classifier.categorize("noextension"));
        assertEquals(FileCategory.BINARY_OR_UNKNOWN, classifier.categorize(null));
        assertEquals(FileCategory.BINARY_OR_UNKNOWN, classifier.categorize("   "));

        assertFalse(classifier.isJavaChange("lib/library.jar"));
    }

    // Scenario 20: a .java file outside the standard Maven roots is reported as
    // Java production (it is Java) but is not analysable at member level.
    @Test
    void javaOutsideStandardRootsIsReportedButNotAnalysable() {
        assertEquals(FileCategory.JAVA_PRODUCTION,
                classifier.categorize("legacy/SideTool.java"));
        assertEquals(ChangedFileClassifier.Category.OTHER,
                classifier.classify("legacy/SideTool.java"));
    }

    // Scenario 21: counts are real (not hardcoded) and every category is present.
    @Test
    void countsByCategoryAreRealAndComplete() {
        Map<FileCategory, Integer> counts = classifier.countByCategory(Arrays.asList(
                "src/main/java/com/sample/Calculator.java",
                "src/main/java/com/sample/Multiplier.java",
                "src/test/java/com/sample/CalculatorTest.java",
                "pom.xml",
                "app.properties",
                "web/app.js",
                "README.md",
                "web/index.html",
                "lib/library.jar"));

        assertEquals(FileCategory.values().length, counts.size(), "every category must be present");
        assertEquals(2, counts.get(FileCategory.JAVA_PRODUCTION));
        assertEquals(1, counts.get(FileCategory.JAVA_TEST));
        assertEquals(1, counts.get(FileCategory.BUILD));
        assertEquals(1, counts.get(FileCategory.CONFIGURATION));
        assertEquals(1, counts.get(FileCategory.OTHER_LANGUAGE));
        assertEquals(1, counts.get(FileCategory.DOCUMENTATION));
        assertEquals(1, counts.get(FileCategory.STATIC_ASSET));
        assertEquals(1, counts.get(FileCategory.BINARY_OR_UNKNOWN));
    }

    // Scenario 22: an empty change set yields all-zero counts.
    @Test
    void emptyChangeSetYieldsAllZeroCounts() {
        Map<FileCategory, Integer> counts = classifier.countByCategory(List.of());
        for (FileCategory category : FileCategory.values()) {
            assertEquals(0, counts.get(category), "expected zero for " + category);
        }
        assertEquals(0, classifier.countByCategory(null).get(FileCategory.JAVA_PRODUCTION));
    }

    @Test
    void windowsSeparatorsAreHandled() {
        assertEquals(FileCategory.JAVA_TEST,
                classifier.categorize("src\\test\\java\\com\\sample\\CalculatorTest.java"));
        assertEquals(FileCategory.BUILD,
                classifier.categorize("C:\\repo\\.mvn\\wrapper\\maven-wrapper.properties"));
    }

    @Test
    void displayNamesAreStable() {
        assertEquals("Java production files", ChangedFileClassifier.displayName(FileCategory.JAVA_PRODUCTION));
        assertEquals("Java test files", ChangedFileClassifier.displayName(FileCategory.JAVA_TEST));
        assertEquals("Build files", ChangedFileClassifier.displayName(FileCategory.BUILD));
        assertEquals("Configuration files", ChangedFileClassifier.displayName(FileCategory.CONFIGURATION));
        assertEquals("Other language files", ChangedFileClassifier.displayName(FileCategory.OTHER_LANGUAGE));
        assertEquals("Documentation files", ChangedFileClassifier.displayName(FileCategory.DOCUMENTATION));
        assertEquals("Static assets", ChangedFileClassifier.displayName(FileCategory.STATIC_ASSET));
        assertEquals("Binary/unknown files", ChangedFileClassifier.displayName(FileCategory.BINARY_OR_UNKNOWN));
    }
}
