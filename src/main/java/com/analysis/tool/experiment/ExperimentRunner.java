package com.analysis.tool.experiment;

import com.analysis.tool.git.GitAnalyzer;
import com.analysis.tool.impact.ChangeImpactAnalyzer;
import com.analysis.tool.impact.FileMapping;
import com.analysis.tool.impact.FileMappingDiscoverer;
import com.analysis.tool.impact.TestSelectionReport;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Runs the test-selection analysis over an ordered list of revisions and
 * produces one row per consecutive pair (revisions[i] -> revisions[i+1]).
 *
 * File pairs are auto-discovered from the union of files at both revisions.
 * This class performs analysis only; it does not execute any tests.
 */
public class ExperimentRunner {

    public static class Row {
        private final String oldRevision;
        private final String newRevision;
        private final int filePairs;
        private final Set<String> selectedTests;
        private final Set<String> untestedMethods;
        private final Set<String> removedMethods;

        public Row(String oldRevision, String newRevision, int filePairs,
                   Set<String> selectedTests, Set<String> untestedMethods, Set<String> removedMethods) {
            this.oldRevision = oldRevision;
            this.newRevision = newRevision;
            this.filePairs = filePairs;
            this.selectedTests = selectedTests;
            this.untestedMethods = untestedMethods;
            this.removedMethods = removedMethods;
        }

        public String getOldRevision() { return oldRevision; }
        public String getNewRevision() { return newRevision; }
        public int getFilePairs() { return filePairs; }
        public Set<String> getSelectedTests() { return selectedTests; }
        public Set<String> getUntestedMethods() { return untestedMethods; }
        public Set<String> getRemovedMethods() { return removedMethods; }
    }

    public List<Row> run(String repoPath, List<String> revisions) throws Exception {
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i + 1 < revisions.size(); i++) {
            String oldRevision = revisions.get(i);
            String newRevision = revisions.get(i + 1);

            List<FileMapping> mappings = discoverMappings(repoPath, oldRevision, newRevision);
            TestSelectionReport report = new ChangeImpactAnalyzer()
                    .analyzeTransitively(repoPath, oldRevision, newRevision, mappings);

            rows.add(new Row(oldRevision, newRevision, mappings.size(),
                    new TreeSet<>(report.getSelectedTests()),
                    new TreeSet<>(report.getUntestedChangedMethods()),
                    new TreeSet<>(report.getRemovedMethods())));
        }
        return rows;
    }

    public static String toMarkdown(List<Row> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("| Range | File pairs | Selected tests | Untested changed methods | Removed methods |\n");
        sb.append("|---|---|---|---|---|\n");
        for (Row row : rows) {
            sb.append("| ").append(row.getOldRevision()).append(" -> ").append(row.getNewRevision())
              .append(" | ").append(row.getFilePairs())
              .append(" | ").append(join(row.getSelectedTests(), ", ", "-"))
              .append(" | ").append(join(row.getUntestedMethods(), ", ", "-"))
              .append(" | ").append(join(row.getRemovedMethods(), ", ", "-"))
              .append(" |\n");
        }
        return sb.toString();
    }

    public static String toCsv(List<Row> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("oldRevision,newRevision,filePairs,selectedCount,selectedTests,untestedMethods,removedMethods\n");
        for (Row row : rows) {
            sb.append(row.getOldRevision()).append(',')
              .append(row.getNewRevision()).append(',')
              .append(row.getFilePairs()).append(',')
              .append(row.getSelectedTests().size()).append(',')
              .append(join(row.getSelectedTests(), ";", "")).append(',')
              .append(join(row.getUntestedMethods(), ";", "")).append(',')
              .append(join(row.getRemovedMethods(), ";", "")).append('\n');
        }
        return sb.toString();
    }

    private static String join(Set<String> values, String separator, String emptyText) {
        return values.isEmpty() ? emptyText : String.join(separator, values);
    }

    private static List<FileMapping> discoverMappings(String repoPath, String oldRevision, String newRevision)
            throws IOException {
        GitAnalyzer git = new GitAnalyzer(repoPath);
        try {
            Set<String> allFiles = new TreeSet<>(git.listFilesAtRevision(oldRevision));
            allFiles.addAll(git.listFilesAtRevision(newRevision));
            return new FileMappingDiscoverer().discover(new ArrayList<>(allFiles));
        } finally {
            git.close();
        }
    }
}
