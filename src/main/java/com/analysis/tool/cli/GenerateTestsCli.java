package com.analysis.tool.cli;

import com.analysis.tool.git.GitAnalyzer;
import com.analysis.tool.testgen.GeneratedTestWriter;
import com.analysis.tool.testgen.IncrementalGenerator;
import com.analysis.tool.testgen.TestEmitter;
import com.analysis.tool.testgen.TestGenerator;

import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Phase 8B / G7: command-line entry point for local test generation.
 *
 * Usage:
 *   GenerateTestsCli <repoPath> <revision> [--previous=<rev>]
 *       [--accept=<Class#signature>]... [--out=<dir>] [--project=<name>]
 *
 * Without --previous an initial suite is generated for the production sources
 * (src/main/java) at <revision>. With --previous the manifest of that revision
 * is read from <out>/<project>/<previous>/ and the suite is updated
 * incrementally: added methods get tests, modified methods keep their old
 * expected values (a difference is reported as BEHAVIOUR_CHANGED), removed
 * methods are retired. --accept re-records the oracle of the named methods
 * (for example --accept=Calculator#sub(int,int)) and overwrites the revision
 * directory; it needs --previous.
 *
 * Output goes to <out> (default generated-tests), never into the target
 * repository. Target code is executed locally and must be trusted.
 *
 * Exit codes: 0 = success (including warnings), 1 = invalid arguments,
 * 2 = generation failed.
 */
public class GenerateTestsCli {

    private static final String USAGE = "Usage: GenerateTestsCli <repoPath> <revision> [--previous=<rev>] "
            + "[--accept=<Class#signature>]... [--out=<dir>] [--project=<name>]";

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    public static int run(String[] args, PrintStream out, PrintStream err) {
        String previous = null;
        String outDir = "generated-tests";
        String project = null;
        Set<String> accepted = new TreeSet<>();
        List<String> positional = new ArrayList<>();

        for (String arg : args) {
            if (arg.startsWith("--previous=")) {
                previous = arg.substring("--previous=".length());
            } else if (arg.startsWith("--out=")) {
                outDir = arg.substring("--out=".length());
            } else if (arg.startsWith("--project=")) {
                project = arg.substring("--project=".length());
            } else if (arg.startsWith("--accept=")) {
                String value = arg.substring("--accept=".length());
                int hash = value.indexOf('#');
                if (hash <= 0 || hash == value.length() - 1) {
                    err.println("Invalid --accept (expected Class#signature): " + value);
                    return 1;
                }
                accepted.add(value.substring(0, hash) + "\t" + value.substring(hash + 1));
            } else if (arg.startsWith("--")) {
                err.println("Unknown option: " + arg);
                err.println(USAGE);
                return 1;
            } else {
                positional.add(arg);
            }
        }

        if (positional.size() != 2 || outDir.isEmpty() || (previous != null && previous.isEmpty())
                || (project != null && project.isEmpty())) {
            err.println(USAGE);
            return 1;
        }
        if (!accepted.isEmpty() && previous == null) {
            err.println("--accept needs --previous");
            return 1;
        }

        String repoPath = positional.get(0);
        String revision = positional.get(1);
        if (project == null) {
            project = new File(repoPath).getAbsoluteFile().getName();
        }
        Path root = Paths.get(outDir);

        try {
            List<String> sources = readProductionSources(repoPath, revision);
            if (sources.isEmpty()) {
                err.println("Generation failed: no .java files under src/main/java at " + revision);
                return 2;
            }

            out.println("Repository     : " + repoPath);
            out.println("Revision       : " + revision);
            out.println("Project        : " + project);
            out.println("Mode           : " + (previous == null ? "initial" : "incremental from " + previous));
            out.println("Sources        : " + sources.size() + " production file(s)");

            TestEmitter.Result result;
            Set<String> behaviourChanged = new TreeSet<>();

            if (previous == null) {
                result = new TestGenerator().generate(sources, project, revision);
            } else {
                Path previousManifest = root.resolve(project).resolve(previous).resolve("manifest.json");
                if (!Files.exists(previousManifest)) {
                    err.println("Generation failed: no manifest for the previous revision at " + previousManifest);
                    return 2;
                }
                String json = Files.readString(previousManifest, StandardCharsets.UTF_8);
                IncrementalGenerator.Outcome outcome =
                        new IncrementalGenerator().generate(json, sources, project, revision, accepted);
                result = outcome.getResult();
                behaviourChanged = outcome.getBehaviourChanged();

                printKeys(out, "Added methods", outcome.getPlan().getAdded());
                printKeys(out, "Modified methods", outcome.getPlan().getModified());
                printKeys(out, "Removed methods (tests retired)", outcome.getPlan().getRemoved());
                out.println("Unchanged      : " + outcome.getPlan().getUnchanged().size() + " method(s)");
            }

            GeneratedTestWriter writer = new GeneratedTestWriter();
            Path directory = accepted.isEmpty()
                    ? writer.write(root, project, revision, result)
                    : writer.replace(root, project, revision, result);

            out.println("Generated tests: " + result.getGeneratedCount());
            out.println("Skipped methods: " + result.getSkippedCount() + " (reasons are in the manifest)");
            out.println("Dropped cases  : " + result.getDroppedCount());
            out.println("Written to     : " + directory);

            if (!behaviourChanged.isEmpty()) {
                printKeys(out, "WARNING - BEHAVIOUR_CHANGED: old expected values kept, these tests fail until accepted",
                        behaviourChanged);
            }
            return 0;
        } catch (Exception e) {
            err.println("Generation failed: " + e.getMessage());
            return 2;
        }
    }

    private static List<String> readProductionSources(String repoPath, String revision) throws Exception {
        GitAnalyzer git = new GitAnalyzer(repoPath);
        try {
            List<String> sources = new ArrayList<>();
            for (String path : git.listFilesAtRevision(revision)) {
                if (path.startsWith("src/main/java/") && path.endsWith(".java")) {
                    sources.add(git.getFileContentAtRevision(revision, path));
                }
            }
            return sources;
        } finally {
            git.close();
        }
    }

    private static void printKeys(PrintStream out, String title, Set<String> keys) {
        if (keys.isEmpty()) {
            return;
        }
        out.println(title + " (" + keys.size() + "):");
        for (String key : keys) {
            out.println("  - " + key.replace('\t', '.'));
        }
    }
}
