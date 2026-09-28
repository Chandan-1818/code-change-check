package com.analysis.tool.cli;

import com.analysis.tool.impact.ChangeImpactAnalyzer;
import com.analysis.tool.impact.FileMapping;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Command-line entry point for change-sensitive regression test selection.
 *
 * Usage:
 *   SelectTestsCli <repoPath> <oldRev> <newRev> <sourceFile=testFile> [<sourceFile=testFile> ...]
 *
 * Each sourceFile=testFile argument pairs a production file with its test
 * file, both relative to the repository root. The tool compares the two
 * revisions and prints the test methods that should be re-run, including
 * tests that reach a changed method through a chain of calls.
 *
 * Exit codes: 0 = success, 1 = invalid arguments, 2 = analysis failed.
 *
 * Current limitation: the source/test file pairs must be supplied by the
 * caller; they are not yet discovered automatically.
 */
public class SelectTestsCli {

    public static void main(String[] args) {
        int exitCode = run(args, System.out, System.err);
        System.exit(exitCode);
    }

    public static int run(String[] args, PrintStream out, PrintStream err) {
        if (args.length < 4) {
            err.println("Usage: SelectTestsCli <repoPath> <oldRev> <newRev> <sourceFile=testFile> [<sourceFile=testFile> ...]");
            return 1;
        }

        String repoPath = args[0];
        String oldRevision = args[1];
        String newRevision = args[2];

        List<FileMapping> mappings = new ArrayList<>();
        for (int i = 3; i < args.length; i++) {
            int separator = args[i].indexOf('=');
            if (separator <= 0 || separator == args[i].length() - 1) {
                err.println("Invalid file pair (expected sourceFile=testFile): " + args[i]);
                return 1;
            }
            mappings.add(new FileMapping(
                    args[i].substring(0, separator),
                    args[i].substring(separator + 1)));
        }

        try {
            Set<String> selectedTests = new TreeSet<>(
                    new ChangeImpactAnalyzer().findImpactedTestsTransitively(
                            repoPath, oldRevision, newRevision, mappings));

            out.println("Repository     : " + repoPath);
            out.println("Compared       : " + oldRevision + " -> " + newRevision);
            out.println("File pairs     : " + mappings.size());
            if (selectedTests.isEmpty()) {
                out.println("Selected tests : none (no impacted tests found)");
            } else {
                out.println("Selected tests (" + selectedTests.size() + "):");
                for (String test : selectedTests) {
                    out.println("  - " + test);
                }
            }
            return 0;
        } catch (Exception e) {
            err.println("Analysis failed: " + e.getMessage());
            return 2;
        }
    }
}
