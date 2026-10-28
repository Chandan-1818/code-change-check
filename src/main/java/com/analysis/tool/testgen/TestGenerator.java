package com.analysis.tool.testgen;

import java.io.IOException;
import java.util.List;

/**
 * Phase 8B / G5 orchestration: discover supported methods, plan cases, record
 * the oracle in separate JVMs, and emit tests plus manifest. Nothing is written
 * to disk here; use GeneratedTestWriter.
 */
public class TestGenerator {

    public TestEmitter.Result generate(List<String> sources, String project, String revision)
            throws IOException, InterruptedException {
        List<DiscoveredMethod> discovered = new TargetDiscoverer().discover(sources);
        List<OracleCase> cases = new CasePlanner().plan(sources);
        List<OracleOutcome> outcomes = new OracleCapture().capture(sources, cases);
        return new TestEmitter().emit(project, revision, discovered, cases, outcomes);
    }
}
