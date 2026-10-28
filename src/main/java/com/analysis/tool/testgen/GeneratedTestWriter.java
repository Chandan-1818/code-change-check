package com.analysis.tool.testgen;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Phase 8B / task G5: writes an emitted suite to
 * &lt;root&gt;/&lt;project&gt;/&lt;revision&gt;/ (root is normally generated-tests/, which is
 * git-ignored and outside the tool's own src/test/java).
 *
 * Never overwrites a different existing suite: if the revision directory already
 * holds a manifest, the new output must be byte-identical (then nothing is
 * written) or the call fails. Replacing an oracle needs the explicit accept
 * command planned for G6.
 */
public class GeneratedTestWriter {

    private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9._-]+");

    public Path write(Path root, String project, String revision, TestEmitter.Result result)
            throws IOException {
        requireSafe("project", project);
        requireSafe("revision", revision);

        Path directory = root.resolve(project).resolve(revision);
        Map<String, String> all = new TreeMap<>(result.getFiles());
        all.put("manifest.json", result.getManifest());

        if (Files.exists(directory.resolve("manifest.json"))) {
            for (Map.Entry<String, String> entry : all.entrySet()) {
                Path file = directory.resolve(entry.getKey());
                if (!Files.exists(file)
                        || !Files.readString(file, StandardCharsets.UTF_8).equals(entry.getValue())) {
                    throw new IllegalStateException("Generated tests already exist at " + directory
                            + " and differ from the new output; refusing to overwrite (" + entry.getKey() + ")");
                }
            }
            return directory;
        }

        for (Map.Entry<String, String> entry : all.entrySet()) {
            Path file = directory.resolve(entry.getKey());
            Files.createDirectories(file.getParent());
            Files.writeString(file, entry.getValue(), StandardCharsets.UTF_8);
        }
        return directory;
    }

    private static void requireSafe(String what, String value) {
        if (value == null || !SAFE_NAME.matcher(value).matches() || value.equals("..") || value.equals(".")) {
            throw new IllegalArgumentException("Unsafe " + what + " for a directory name: " + value);
        }
    }
}
