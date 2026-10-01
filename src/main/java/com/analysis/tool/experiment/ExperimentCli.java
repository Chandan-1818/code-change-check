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
 *   ExperimentCli <repoPath> <rev1> <rev2> [<rev3> ...] [--csv=<file>]
 *
 * Analyses each consecutive pair of revisions (rev1 -> rev2, rev2 -> rev3, ...)
 * with auto-discovered file pairs and prints a Markdown table. If --csv is
 * given, the same rows are also written to that file as CSV. Analysis only:
 * no tests are executed.
 *
 * Exit codes: 0 = success, 1 = invalid arguments, 2 = analysis failed.
 */
public class ExperimentCli {

    private static final String CSV_PREFIX = "--csv=";

    public static void main(String[] args) {
        int exitCode = run(args, System.out, System.err);
        System.exit(exitCode);
    }

    public static int run(String[] args, PrintStream out, PrintStream err) {
        String csvPath = null;
        List<String> positional = new ArrayList<>();
        for (String arg : args) {
            if (arg.startsWith(CSV_PREFIX)) {
                csvPath = arg.substring(CSV_PREFIX.length());
            } else {
                positional.add(arg);
            }
        }

        if (positional.size() < 3 || (csvPath != null && csvPath.isEmpty())) {
            err.println("Usage: ExperimentCli <repoPath> <rev1> <rev2> [<rev3> ...] [--csv=<file>]");
            return 1;
        }

        String repoPath = positional.get(0);
        List<String> revisions = positional.subList(1, positional.size());

        try {
            List<ExperimentRunner.Row> rows = new ExperimentRunner().run(repoPath, revisions);

            out.println("Repository : " + repoPath);
            out.println("Revisions  : " + revisions.size());
            out.println();
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
