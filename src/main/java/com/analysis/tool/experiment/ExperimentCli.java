package com.analysis.tool.experiment;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Command-line entry point for the multi-revision experiment.
 *
 * Usage:
 *   ExperimentCli <repoPath> <rev1> <rev2> [<rev3> ...] [--csv=<file>] [--execute]
 *
 * Analyses each consecutive pair of revisions (rev1 -> rev2, rev2 -> rev3, ...)
 * with auto-discovered file pairs and prints a Markdown table.
 *
 * Without --execute this is analysis only: no tests are run. If --csv is
 * given, the selection rows are also written to that file as CSV.
 *
 * With --execute, the full and selected test suites are also run through
 * real Maven builds in a temporary checkout of each revision, so the test
 * reduction figures come from the suite that existed at that revision. This
 * is slow (two Maven runs per range). --csv is not supported together with
 * --execute yet.
 *
 * Exit codes: 0 = success, 1 = invalid arguments, 2 = analysis failed.
 */
public class ExperimentCli {

    private static final String CSV_PREFIX = "--csv=";
    private static final String EXECUTE_FLAG = "--execute";

    public static void main(String[] args) {
        int exitCode = run(args, System.out, System.err);
        System.exit(exitCode);
    }

    public static int run(String[] args, PrintStream out, PrintStream err) {
        String csvPath = null;
        boolean execute = false;
        List<String> positional = new ArrayList<>();
        for (String arg : args) {
            if (arg.startsWith(CSV_PREFIX)) {
                csvPath = arg.substring(CSV_PREFIX.length());
            } else if (EXECUTE_FLAG.equals(arg)) {
                execute = true;
            } else {
                positional.add(arg);
            }
        }

        if (positional.size() < 3 || (csvPath != null && csvPath.isEmpty())) {
            err.println("Usage: ExperimentCli <repoPath> <rev1> <rev2> [<rev3> ...] [--csv=<file>] [--execute]");
            return 1;
        }
        if (execute && csvPath != null) {
            err.println("--csv is not supported together with --execute yet");
            return 1;
        }

        String repoPath = positional.get(0);
        List<String> revisions = positional.subList(1, positional.size());

        try {
            out.println("Repository : " + repoPath);
            out.println("Revisions  : " + revisions.size());

            if (execute) {
                out.println("Mode       : executed (real Maven runs in a temporary checkout of each revision)");
                out.println();
                List<ExperimentExecutor.ExecutedRow> rows = new ExperimentExecutor().run(repoPath, revisions);
                out.print(ExperimentExecutor.toMarkdown(rows));
                return 0;
            }

            out.println();
            List<ExperimentRunner.Row> rows = new ExperimentRunner().run(repoPath, revisions);
            out.print(ExperimentRunner.toMarkdown(rows));

            if (csvPath != null) {
                Files.writeString(Paths.get(csvPath), ExperimentRunner.toCsv(rows));
                out.println();
                out.println("CSV written to: " + csvPath);
            }
            return 0;
        } catch (Exception e) {
            err.println("Experiment failed: " + e.getMessage());
            return 2;
        }
    }
}
