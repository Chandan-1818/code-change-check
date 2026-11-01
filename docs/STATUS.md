# Implementation status (evidence-based)

Date: 2026-11-01. Commit tested: 1e40736 plus the audit fixes listed below (branch safety-fixes-v3).
Evidence: docs/verification/2026-11-01-audit-fix-mvn-test.txt
(mvn -B clean test, all tests: 194 run, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS, JDK 23.0.2,
compiled to target 17). Earlier runs: docs/verification/2026-11-01-mvn-test-g8.txt (commit 979146c,
191 run), docs/verification/2026-10-30-mvn-test-full.txt (commit 77fe866,
164 run), docs/verification/2026-10-26-mvn-test-*.txt (commit b0aa554).

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
| G8 fault-detection gain (two arms) | VERIFIED on the sample (V16) | FaultDetectionExperimentTest, docs/verification/2026-11-01-g8-experiment.txt, experiments/fault-detection-report.md | single-line faults only (GAP-17); coverage is static reachability, not runtime |
| Sample path configurable | VERIFIED | SamplePathsTest | GAP-18 closed |
| Phase 8B test generation | IN PROGRESS | see below | TestSourceProvider |
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
| G8 metrics (coverage before/after, validity, fault-detection gain) | VERIFIED on the sample (V16) | FaultDetectionExperimentTest (17 tests), docs/verification/2026-11-01-g8-experiment.txt, experiments/fault-detection-report.md, experiments/fault-detection-results.json |

## G8 fault-detection experiment (measured 2026-11-01)

Command: `FaultDetectionExperiment D:/PROJECT/SAMPLE-PROJECT/sample-project 7059f4c experiments sample-project`.
Evidence: docs/verification/2026-11-01-g8-experiment.txt, docs/verification/2026-11-01-g8-focused-tests.txt,
experiments/fault-detection-report.md, experiments/fault-detection-results.json.
Sample: revision 7059f4c (Version 16). One mutant commit is built per fault and BOTH arms run against that
same mutant, so the injected mutation is identical by construction; the mutant commit id and the SHA-256 of
the mutated file are recorded per fault in the JSON.

| Measure | Manual only | Manual + generated |
|---|---|---|
| Detected | 15 | 16 |
| Undetected | 2 | 1 |
| Invalid (stillborn, did not compile) | 0 | 0 |
| Inconclusive (no usable verdict) | 0 | 0 |
| Mutation score | 15/17 = 0.8824 | 16/17 = 0.9412 |

Additional detection attributable to the generated tests: 1 (Calculator.negate).
Regressions (killed by the manual suite, missed once the generated tests are added): 0.
Undetected in both arms: 1 (CheckoutService.quote; its stock guard is not exercised by either suite).
Suites: manual 17 tests; generated 98 tests (5 methods skipped by the generator, reasons in the manifest).
Coverage proxy: static reachability, explicitly NOT runtime line/branch coverage. Manual 15/18 (83.33%),
manual + generated 17/18 (94.44%); newly reached methods Calculator.negate and CheckoutService.quote
(being reachable is not the same as being killed - CheckoutService.quote stays undetected).
Reproducibility: the target repository is cloned into a temporary directory that is deleted afterwards;
the sample repository working tree is left unmodified by the run.

Real-history result (GenerateTestsCliRealHistoryTest): initial suite at V13 (98 tests);
V13 to V14 reports TaxCalculator.calculateTax(int) as modified and BEHAVIOUR_CHANGED; V14 to V15
adds CheckoutService.quote(String,int) (110 tests); V15 to V16 retires Calculator.compute(int,int)
(98 tests). Running the V14 suite at V14: 116 tests, 113 passed, 3 failed, all three failing tests
being gen_TaxCalculator_calculateTax_02, _04, _05 (the intended behaviour-change signal).

Caveats: the sample already had tests, so the no-existing-tests case is covered only up to
compilation (TestGeneratorTest). Pinned oracles do not detect a change in a callee. Dependabot
reports 1 moderate alert; package not yet identified. JDK 17 re-run outstanding.

## Audit fixes (2026-11-01)

| Issue | Root cause | Fix | Regression test |
|---|---|---|---|
| FaultInjectionRunner aborted the whole run when a fault could not be injected at the pinned revision (file missing, or find-text not present exactly once). Reachable in practice: `sampleProjectFaults()` still lists `Calculator.compute`, which is absent from V16. | the fault file was read with `Files.readString` without an existence check, and a non-unique find-text threw `IllegalStateException` | one shared guard `FaultInjectionRunner.injectionProblem(Path, Fault)` used by both harnesses; the fault is recorded as the new `Outcome.SKIPPED` with its reason and excluded from every precision/recall denominator; G8 records it as an unsupported mutation (mutantCommit null) | FaultInjectionRunnerTest.shouldLocateOnlyASingleOccurrence, shouldExplainWhyAFaultCannotBeInjected, shouldRecordAnUninjectableFaultAsSkipped |
| Deprecated JGit `CheckoutCommand.setForce(boolean)` at 4 call sites | JGit 6.10 renamed it to `setForced(boolean)` | renamed at all 4 call sites (`GeneratedSuiteRunner`, `FaultInjectionRunner`, `FaultDetectionExperiment` x2); `mvn -B clean compile -Dmaven.compiler.showDeprecation=true` is now warning-free | compile check only - the rename is semantically identical |

Checked and found correct, no change made: `GitAnalyzer` closes every `RevWalk`/`TreeWalk`/`CanonicalTreeParser`
via try-with-resources; every `Files.walk`/`Files.list` in `src/main` is inside try-with-resources;
`OracleRuntime` reads its timeout through the primitive-`long` `Long.getLong` overload, so it cannot
null-unbox; no network or AI-service call exists in `src/main` (the only three dependencies are JUnit 5,
JGit and JavaParser).

## Slow tests (launch Maven; add to CLAUDE.md section 4)
TestRunnerIntegrationTest, EvaluationMetricsIntegrationTest, SelectTestsCliExecuteTest,
ExperimentExecutorTest, ExperimentCliExecuteTest, FaultInjectionRunnerTest,
FaultInjectionGrownSampleTest, GeneratedSuiteRunnerIntegrationTest, GenerateTestsCliRealHistoryTest.
