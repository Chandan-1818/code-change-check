# Implementation status (evidence-based)

Date: 2026-10-30. Commit tested: 77fe866 (branch safety-fixes-v3).
Evidence: docs/verification/2026-10-30-mvn-test-full.txt
(mvn -B test, all tests: 164 run, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS, JDK 23.0.2,
compiled to target 17). Earlier runs: docs/verification/2026-10-26-mvn-test-*.txt (commit b0aa554).

Status words follow CLAUDE.md section 5. A component is VERIFIED only where the recorded run
executes it and passes. Passing tests do not close a gap in the gap register.

| Component | Status | Evidence | Open gaps |
|---|---|---|---|
| Git access | VERIFIED | GitAnalyzerTest, GitAnalyzerListFilesTest in the full run | GAP-19 (.git must be a directory) |
| Edge cases (untested, removed, deleted, new file) | VERIFIED | CLI and ChangeImpactAnalyzer tests in the full run | GAP-11 |
| Reduction metrics | VERIFIED | EvaluationMetricsIntegrationTest | time reduction unreliable on small suites |
| Sample project V1-V16 | VERIFIED | all 16 hashes resolve; tests pass at pinned revisions | repo is outside this repository |
| Method change detection | IN PROGRESS | MethodChangeDetectorSafetyTest, NonMethodChangeDetectorTest pass | name-only identity (GAP-01, 06) |
| Dependency graph, impact, selection | IN PROGRESS | tests pass | GAP-05, 07, 08, 09, 10, 13 |
| CLI tools, test execution | IN PROGRESS | tests pass | GAP-14, 15, 16 |
| Fault injection (9 at V8, 17 at V16) | VERIFIED for zero MISSED on these faults | FaultInjectionRunnerTest, FaultInjectionGrownSampleTest | single-line faults only, GAP-17 |
| Sample path configurable | VERIFIED | SamplePathsTest | GAP-18 closed |
| Phase 8B test generation | IN PROGRESS | see below | G8, TestSourceProvider |
| Preflight, selection confidence | NOT STARTED | none | GAP-12 |
| Backend, database, dashboard, report | NOT STARTED | none | GAP-26 |

## Phase 8B tasks

| Task | Status | Evidence |
|---|---|---|
| G1 spec, G2 discovery, G3 inputs and receivers, G4 oracle capture | VERIFIED | unit and integration tests in the full run |
| G5 emitter, manifest, writer | VERIFIED | TestEmitterTest, GeneratedTestWriterTest, TestGeneratorTest |
| G6 incremental update (hash, pinned oracle, accept, retire) | VERIFIED | IncrementalGeneratorTest and 4 other classes |
| G7 execution of generated suite | VERIFIED on the sample | GeneratedSuiteRunnerIntegrationTest: 98 generated tests, 5 methods skipped, 115 of 115 passed at V16 |
| G7 CLI (GenerateTestsCli) | VERIFIED | GenerateTestsCliTest, GenerateTestsCliRealHistoryTest |
| TestSourceProvider (generated tests in selection) | NOT STARTED | none |
| G8 metrics (coverage before/after, validity, fault-detection gain) | NOT STARTED | none |

Real-history result (GenerateTestsCliRealHistoryTest): initial suite at V13 (98 tests);
V13 to V14 reports TaxCalculator.calculateTax(int) as modified and BEHAVIOUR_CHANGED; V14 to V15
adds CheckoutService.quote(String,int) (110 tests); V15 to V16 retires Calculator.compute(int,int)
(98 tests). Running the V14 suite at V14: 116 tests, 113 passed, 3 failed, all three failing tests
being gen_TaxCalculator_calculateTax_02, _04, _05 (the intended behaviour-change signal).

Caveats: the sample already had tests, so the no-existing-tests case is covered only up to
compilation (TestGeneratorTest). Pinned oracles do not detect a change in a callee. Dependabot
reports 1 moderate alert; package not yet identified. JDK 17 re-run outstanding.

## Slow tests (launch Maven; add to CLAUDE.md section 4)
TestRunnerIntegrationTest, EvaluationMetricsIntegrationTest, SelectTestsCliExecuteTest,
ExperimentExecutorTest, ExperimentCliExecuteTest, FaultInjectionRunnerTest,
FaultInjectionGrownSampleTest, GeneratedSuiteRunnerIntegrationTest, GenerateTestsCliRealHistoryTest.
