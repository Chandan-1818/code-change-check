package com.analysis.tool.impact;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Path-based classification of changed files (H4). No Git, no parsing, so it
 * is fast and portable.
 */
public class ChangedFileClassifierTest {

    private final ChangedFileClassifier classifier = new ChangedFileClassifier();

    @Test
    public void javaSourcesAndTestsAreAnalysable() {
        assertEquals(ChangedFileClassifier.Category.JAVA_SOURCE,
                classifier.classify("src/main/java/com/sample/Calculator.java"));
        assertEquals(ChangedFileClassifier.Category.JAVA_TEST,
                classifier.classify("src/test/java/com/sample/CalculatorTest.java"));

        assertFalse(classifier.isUnanalysableChange("src/main/java/com/sample/Calculator.java"));
        assertFalse(classifier.isUnanalysableChange("src/test/java/com/sample/CalculatorTest.java"));
    }

    @Test
    public void mavenAndGradleBuildFilesAreClassified() {
        assertEquals(ChangedFileClassifier.Category.BUILD, classifier.classify("pom.xml"));
        assertEquals(ChangedFileClassifier.Category.BUILD, classifier.classify("build.gradle"));
        assertEquals(ChangedFileClassifier.Category.BUILD, classifier.classify("build.gradle.kts"));
        assertEquals(ChangedFileClassifier.Category.BUILD, classifier.classify("settings.gradle"));
        assertEquals(ChangedFileClassifier.Category.BUILD,
                classifier.classify(".mvn/wrapper/maven-wrapper.properties"));

        assertTrue(classifier.isUnanalysableChange("pom.xml"));
        assertTrue(classifier.isUnanalysableChange("build.gradle"));
    }

    @Test
    public void resourcesAreClassified() {
        assertEquals(ChangedFileClassifier.Category.RESOURCE,
                classifier.classify("src/main/resources/application.properties"));
        assertEquals(ChangedFileClassifier.Category.RESOURCE,
                classifier.classify("src/test/resources/fixtures/data.json"));

        assertTrue(classifier.isUnanalysableChange("src/main/resources/logback.xml"));
    }

    @Test
    public void otherNonJavaFilesAreUnanalysable() {
        assertEquals(ChangedFileClassifier.Category.OTHER, classifier.classify("README.md"));
        assertEquals(ChangedFileClassifier.Category.OTHER, classifier.classify("scripts/deploy.sh"));

        assertTrue(classifier.isUnanalysableChange("README.md"));
    }

    @Test
    public void unanalysableChangesFiltersAndDeduplicates() {
        Set<String> result = classifier.unanalysableChanges(Arrays.asList(
                "src/main/java/com/sample/Calculator.java",
                "pom.xml",
                "pom.xml",
                "src/main/resources/application.properties",
                "src/test/java/com/sample/CalculatorTest.java"));

        assertEquals(Set.of("pom.xml", "src/main/resources/application.properties"), result);
    }

    @Test
    public void nullAndEmptyAreOtherAndUnanalysable() {
        assertEquals(ChangedFileClassifier.Category.OTHER, classifier.classify(null));
        assertTrue(classifier.isUnanalysableChange(""));
        assertTrue(classifier.unanalysableChanges(null).isEmpty());
    }

    @Test
    public void windowsSeparatorsAreHandled() {
        assertEquals(ChangedFileClassifier.Category.JAVA_SOURCE,
                classifier.classify("src\\main\\java\\com\\sample\\Calculator.java"));
        assertTrue(classifier.isBuildFile("C:\\repo\\.mvn\\wrapper\\maven-wrapper.properties"));
    }
}
