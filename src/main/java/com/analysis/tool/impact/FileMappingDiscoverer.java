package com.analysis.tool.impact;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Derives source/test file pairs from a list of repository file paths,
 * using the Maven naming convention: src/main/java/pkg/Foo.java pairs with
 * src/test/java/pkg/FooTest.java.
 *
 * Only .java files under src/main/java are treated as sources. Test files,
 * build files and other resources are ignored. A source whose conventional
 * test file does not exist is still paired: the missing test file reads as
 * empty in ChangeImpactAnalyzer, so its changed methods are reported as
 * untested.
 *
 * Limitation: this is a naming convention, not a guarantee. A test that
 * covers a class under a different name is not paired with it.
 */
public class FileMappingDiscoverer {

    private static final String MAIN_PREFIX = "src/main/java/";
    private static final String TEST_PREFIX = "src/test/java/";
    private static final String JAVA_SUFFIX = ".java";

    public List<FileMapping> discover(List<String> filePaths) {
        TreeSet<String> sources = new TreeSet<>();
        for (String path : filePaths) {
            if (path.startsWith(MAIN_PREFIX) && path.endsWith(JAVA_SUFFIX)) {
                sources.add(path);
            }
        }

        List<FileMapping> mappings = new ArrayList<>();
        for (String source : sources) {
            String withoutExtension = source.substring(
                    MAIN_PREFIX.length(), source.length() - JAVA_SUFFIX.length());
            mappings.add(new FileMapping(source, TEST_PREFIX + withoutExtension + "Test" + JAVA_SUFFIX));
        }
        return mappings;
    }
}
