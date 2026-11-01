package com.analysis.tool.testgen;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Phase 8B / G6: generates the suite for a new revision from the previous
 * manifest. For a MODIFIED method the previous oracle is kept (pinned); if the
 * new behaviour differs the test is marked BEHAVIOUR_CHANGED and will fail when
 * run, so a behaviour change cannot pass unnoticed. The oracle is re-recorded
 * only for method keys listed in acceptedMethodKeys (the explicit accept).
 */
public class IncrementalGenerator {

    public static final class Outcome {
        private final TestEmitter.Result result;
        private final IncrementalPlanner.Plan plan;
        private final Set<String> behaviourChanged;

        Outcome(TestEmitter.Result result, IncrementalPlanner.Plan plan, Set<String> behaviourChanged) {
            this.result = result;
            this.plan = plan;
            this.behaviourChanged = behaviourChanged;
        }

        public TestEmitter.Result getResult() { return result; }
        public IncrementalPlanner.Plan getPlan() { return plan; }
        /** Method keys (class TAB signature) whose pinned oracle no longer matches. */
        public Set<String> getBehaviourChanged() { return behaviourChanged; }
    }

    public Outcome generate(String previousManifestJson, List<String> sources, String project,
                            String revision, Set<String> acceptedMethodKeys)
            throws IOException, InterruptedException {
        List<DiscoveredMethod> discovered = new TargetDiscoverer().discover(sources);
        List<OracleCase> cases = new CasePlanner().plan(sources);
        List<OracleOutcome> outcomes = new OracleCapture().capture(sources, cases);
        return apply(previousManifestJson, sources, project, revision, acceptedMethodKeys,
                discovered, cases, outcomes);
    }

    /** Everything except the JVM capture; separate so it can be tested with synthetic outcomes. */
    Outcome apply(String previousManifestJson, List<String> sources, String project, String revision,
                  Set<String> acceptedMethodKeys, List<DiscoveredMethod> discovered,
                  List<OracleCase> cases, List<OracleOutcome> outcomes) {
        ManifestReader.Data previous = previousManifestJson == null || previousManifestJson.trim().isEmpty()
                ? ManifestReader.Data.empty()
                : new ManifestReader().read(previousManifestJson);

        Map<String, String> allHashes = new MethodHasher().hash(sources);
        Map<String, String> supportedHashes = new TreeMap<>();
        for (DiscoveredMethod method : discovered) {
            if (method.isSupported()) {
                String key = MethodKey.of(method.getClassName(), method.getSignature());
                supportedHashes.put(key, allHashes.getOrDefault(key, ""));
            }
        }

        IncrementalPlanner.Plan plan = new IncrementalPlanner().plan(previous, supportedHashes);

        List<OracleOutcome> finalOutcomes = new ArrayList<>();
        List<String> states = new ArrayList<>();
        Set<String> behaviourChanged = new TreeSet<>();

        for (int i = 0; i < cases.size(); i++) {
            OracleCase c = cases.get(i);
            OracleOutcome fresh = outcomes.get(i);
            String methodKey = MethodKey.of(c.getClassName(), MethodKey.signature(c));
            String state = "ACTIVE";
            OracleOutcome use = fresh;

            if (plan.getModified().contains(methodKey)
                    && !acceptedMethodKeys.contains(methodKey)
                    && fresh.isUsable()) {
                ManifestReader.Expected old = previous.getExpectedByCase().get(
                        MethodKey.ofCase(c.getClassName(), MethodKey.signature(c), c.getArgumentExpressions()));
                if (old != null) {
                    OracleOutcome pinned = new OracleOutcome(
                            "EXCEPTION".equals(old.getKind())
                                    ? OracleOutcome.Status.THREW : OracleOutcome.Status.RETURNED,
                            old.getValue());
                    if (!pinned.equals(fresh)) {
                        state = "BEHAVIOUR_CHANGED";
                        behaviourChanged.add(methodKey);
                    }
                    use = pinned;
                }
            }
            finalOutcomes.add(use);
            states.add(state);
        }

        List<String[]> retired = new ArrayList<>(previous.getRetired());
        for (String key : plan.getRemoved()) {
            for (String testMethod : previous.getTestsByMethod().getOrDefault(key, new ArrayList<>())) {
                retired.add(new String[] {testMethod, revision});
            }
        }

        TestEmitter.Result result = new TestEmitter().emit(project, revision, discovered, cases,
                finalOutcomes, allHashes, states, retired);
        return new Outcome(result, plan, behaviourChanged);
    }
}
