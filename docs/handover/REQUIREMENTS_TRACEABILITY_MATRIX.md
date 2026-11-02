# REQUIREMENTS_TRACEABILITY_MATRIX.md

Sources: synopsis sections 7, 9, 10, 18; blueprint sections 2, 12-14, 19; roadmap section 3; the TRAE handover prompt's mandatory list.
**Verification column:** no row is VERIFIED. "NV" = IMPLEMENTED — NOT VERIFIED (code and tests exist, never run with recorded output). "IP" = IN PROGRESS. "NS" = NOT STARTED.
Test names are abbreviated; full lists are in `SOURCE_CODE_INVENTORY.md` section 5.

## A. Functional requirements (synopsis section 9)

| ID | Requirement | Implementation | Source files | Existing tests | Status | Remaining work |
|---|---|---|---|---|---|---|
| FR1 | Point to a Maven Java project | CLI takes a repo path; preflight validates the target repository first (single-module, JUnit 5, `.git` directory) and refuses/reduces otherwise (H9) | `SelectTestsCli`, `ExperimentCli`, `preflight/Preflight`, `preflight/PreflightResult` | `SelectTestsCliTest` (exit 2 on bad path), `ExperimentCliTest`, `PreflightTest`, `PreflightResultTest`, `PreflightCliIntegrationTest` | IP | Preflight DONE (H9, verified 2026-11-01-h9-focused-tests.txt / 2026-11-01-mvn-test-h9.txt); packaged entry (Ph. 12), API (Ph. 13) |
| FR2 | Detect changes between two versions | Git file diff, method and non-method detection; member-level (arity-aware) detection incl. constructors (H3); class-level (record/annotation-type) fingerprints and file classification for build/resource changes (H4) | `GitAnalyzer`, `JavaParserAnalyzer`, `MethodChangeDetector`, `MemberChangeDetector`, `MemberId`, `NonMethodChangeDetector`, `ChangedFileClassifier` | `GitAnalyzerTest`, `MethodChangeDetector*Test`, `MemberChangeDetectorTest`, `MemberIdTest`, `NonMethodChangeDetectorTest`, `ChangedFileClassifierTest` | IP | Stage A identity DONE (H3, verified 2026-11-01-mvn-test-h3.txt); class/record/build/resource triggers DONE (H4, verified 2026-11-01-mvn-test-h4.txt) |
| FR3 | Analyse call, inheritance, field-usage dependencies | Calls (name-based) plus constructor edges with resolved class (H3) | `DependencyGraph`, `DependencyGraphBuilder` | `DependencyGraphBuilderIntegrationTest`, `DependencyGraphArityTest` | IP | Dispatch, inheritance and field edges; Symbol Solver stage B or documented limitation |
| FR4 | Identify impacted classes/methods | Reverse BFS; configurable depth (unlimited default, H6); member-id entry with seed-hop arity narrowing (H3); parent links for explanation chains (H7) | `ImpactAnalyzer`, `ImpactSet` | `ImpactAnalyzerIntegrationTest`, `ImpactAnalyzerDepthTest`, `MemberAwareSelectionIntegrationTest`, `ImpactSetExplanationChainTest` | IP | Parent links and deleted-method callers DONE (H7, verified 2026-11-01-mvn-test-h7.txt) |
| FR5 | Identify related JUnit tests | Repo-wide discovery of `src/test/java/**.java` (`TestSourceProvider`); reachability to any JUnit 5 test-annotation node, including lifecycle attribution for `@Nested` (H7) | `ChangeImpactAnalyzer`, `TestSourceProvider`, `FileMappingDiscoverer` | `...TransitiveIntegrationTest`, `...OrderIntegrationTest`, `FileMappingDiscovererTest`, `TestSourceProviderTest`, `H7TestDiscoveryIntegrationTest` | IP | Discovery, other annotations and lifecycle DONE (H7, verified 2026-11-01-mvn-test-h7.txt) |
| FR6 | Select relevant subset | `analyzeTransitively` over `MemberId` changes (arity-aware, conservative fallback); added/modified test methods selected unconditionally; removed-method callers reported (H7) | `ChangeImpactAnalyzer`, `TestSelectionReport`, `TestSourceProvider` | `ChangeImpactAnalyzer*Test`, `MemberAwareSelectionIntegrationTest`, `ExperimentRunnerTest`, `LargerSampleHistoryTest`, `H7TestDiscoveryIntegrationTest` | IP | Added/modified test selection DONE (H7, verified 2026-11-01-mvn-test-h7.txt); Ph. 9 outstanding |
| FR7 | Execute selected tests via Maven/JUnit | `mvn test -Dtest=...` subprocess | `TestRunner`, `TestRunResult`, `IsolatedRevisionCheckout`, `ExperimentExecutor` | `TestRunnerIntegrationTest`, `TestRunnerExecutionHardeningTest`, `IsolatedRevisionCheckoutTest`, `SurefireReportParserTest`, `TestRunnerParseResultTest`, `ExperimentExecutorTest`, `SelectTestsCliExecuteTest` | VERIFIED (H8) | Timeout (configurable, process-tree kill), isolated temp checkout of the new revision, per-test Surefire XML, failure-vs-error, compile-failure detection all DONE and verified 2026-11-01-h8-focused-tests.txt / 2026-11-01-mvn-test-h8.txt |
| FR8 | Display changes, impact, selection, results | Console and Markdown tables | `SelectTestsCli`, `ExperimentRunner`, `ExperimentExecutor` | CLI and experiment tests | IP (console only) | Impact output, confidence, dashboard (Ph. 13, 15) |
| FR9 | Store/report results | CSV and Markdown only | `ExperimentRunner.toCsv` | `ExperimentCliTest` (CSV) | IP | DB or documented decision (Ph. 14); recorded result files |
| **FR10** | **Generate JUnit tests locally, including for projects with no tests** | **None** | **none** | **none** | **NS** | **Phase 8B G1-G8. Must pass acceptance (1)** |
| **FR11** | **Generate new/updated tests when source changes** | **None** | change detection exists as input (`MethodChangeResult`) | none | **NS** | **G6 incremental update** |
| **FR12** | **All generation and execution local; no external AI/cloud** | Currently satisfied vacuously (no such dependency in `pom.xml`) | `pom.xml` | none | NS | Recheck `pom.xml` and code at the end of 8B; add a test or script that scans for network/AI dependencies |
| **FR13** | **Generated tests and manifest stored separately from tool's tests** | None | `.gitignore` lacks `generated-tests/` | none | **NS** | Add ignore entry; G5/G7 layout; tests proving separation (TS-24) |
| **FR14** | **Report methods that could not be generated, with reasons** | None | none | none | **NS** | G2 reason codes, manifest `skipped` list, CLI/report output |

## B. Objectives (synopsis section 7)

| ID | Objective | Implementation | Tests | Status | Remaining |
|---|---|---|---|---|---|
| O1 | Change detection | see FR2 | see FR2 | IP | see FR2 |
| O2 | Static impact analysis | see FR3/FR4 | | IP | |
| O3 | Test-to-code mapping | `ChangeImpactAnalyzer` test discovery + call graph; `TestSourceProvider` (H7) | `TestSourceProviderTest`, `H7TestDiscoveryIntegrationTest` | IP | H7 discovery DONE (2026-11-01-mvn-test-h7.txt); enum/record containers remain (GAP-13 partial) |
| O4 | Selection module | see FR6 | | IP | |
| O5 | Maven/JUnit integration | `TestRunner` in an isolated new-revision checkout; per-test Surefire XML parsing; timeout and compile-failure handling (H8) | `TestRunnerIntegrationTest`, `TestRunnerExecutionHardeningTest`, `IsolatedRevisionCheckoutTest` | VERIFIED (H8) | Correct-revision execution, timeout and per-test results DONE (2026-11-01-h8-focused-tests.txt); `mvn`-only assumption remains |
| O6 | Evaluate vs full suite | `EvaluationMetrics`, `ExperimentExecutor`, `FaultInjectionRunner` | `EvaluationMetricsIntegrationTest`, fault tests | IP | Record data (Ph. 18-19) |
| O7 | Reviewable report/interface | Console/Markdown | | IP | Dashboard (Ph. 15) |
| **O8** | **Local automatic test generation** | **None** | none | **NS** | **Phase 8B** |

## C. Non-functional requirements (synopsis section 10)

| Attribute | Current | Status | Remaining |
|---|---|---|---|
| Performance | O(N) caller scan; no measurement of analysis time | IP | Index reverse lookup; report analysis time with suite time |
| Accuracy | Name-based over-selects; safety gaps U under-select | IP | Close U gaps; fault-injection recall |
| Scalability | Untested beyond 16-revision sample | NS | Indexed graph; compatibility study |
| Maintainability | Packages loosely coupled; duplicate `discoverMappings` | IP | Deduplicate only as separate change |
| Usability | Console only | IP | Dashboard |
| Reliability (same input, same output) | `TreeSet`/sorted output in CLI; `HashSet` internally | IP | Determinism test |
| Determinism and locality (generator) | n/a | NS | G4/G5 byte-identical test |
| Isolation (generator) | n/a | NS | Separate JVM, timeout, separate storage |

## D. Mandatory requirements from the TRAE handover prompt

| ID | Requirement | Status | Implementation / remaining |
|---|---|---|---|
| MR-1 | Automatic test generation for Java source | NS | 8B |
| MR-2 | Generate tests when no existing suite | NS | 8B acceptance (1); fixture: temp repo with sources and no `src/test` |
| MR-3 | Additional/updated tests on source change | NS | G6 |
| MR-4 | Generation fully local | NS | design: no external service; check `pom.xml` |
| MR-5 | Execution fully local | IMPLEMENTED — NOT VERIFIED | `TestRunner` local `mvn` |
| MR-6 | No external AI APIs / remote generation / cloud | Satisfied vacuously | recheck at end of 8B |
| MR-7 | Integration with Git change detection | NS | G6 consumes `MethodChangeResult` per revision pair |
| MR-8 | Integration with impact analysis | NS | G7 adds generated tests to graph |
| MR-9 | Integration with dependency graph | H7 manual provider DONE (`TestSourceProvider` discovers manual test sources and associates them through `buildFromSources`); Phase 8B generated-source integration NS | `TestSourceProviderTest`, `H7TestDiscoveryIntegrationTest` | IP (manual) / NS (generated) | G7 feeds generated sources through `buildFromSources` (Phase 8B) |
| MR-10 | Integration with regression test selection | NS | selection report must separate manual/generated |
| MR-11 | Integration with test execution | NS | copy into temp checkout only |
| MR-12 | Generated tests separate from the tool's own tests | NS | `generated-tests/<project>/<revision>/`, git-ignored, tool tests in `com.analysis.tool.testgen` |
| MR-13 | Never overwrite manual tests or accepted oracles | NS | G6 policy and tests |

## E. Evaluation metrics (synopsis section 18)

| Metric | Implementation | Tests | Status | Remaining |
|---|---|---|---|---|
| Test reduction % | `EvaluationMetrics` | `EvaluationMetricsIntegrationTest`, execute tests, `ExperimentExecutorTest` (60.0% at V5) | NV | "n/a" handling, synthetic unit test |
| Time reduction % | `EvaluationMetrics` | informational only | NV | 3-run average, larger target |
| Precision | `eval/PrecisionRecall` (exact); `FaultInjectionEvaluator` (experiment); `FaultInjectionRunner.percent` (legacy proxy) | `PrecisionRecallTest`, `EvaluationReportTest`, `GroundTruthEvaluatorTest`, `GroundTruthEvaluationIntegrationTest` | VERIFIED (H10) | Stated as lower bound (D-16); experiment result 0.6727 over 17 faults; docs/verification/2026-11-01-h10-ground-truth-experiment.txt |
| Recall | `eval/PrecisionRecall` (exact); `FaultInjectionEvaluator` (experiment) | same as Precision | VERIFIED (H10) | Recall=1.0000 exact (zero FN) across 17 faults; docs/verification/2026-11-01-h10-ground-truth-experiment.txt; GAP-17 CLOSED |
| Selection effectiveness | pass/fail compare via `ExperimentExecutor.isHealthy` | `ExperimentExecutorTest` | IP | Recorded runs |
| Method test coverage (generator) | none | none | NS | 13.4 |
| Generation success/validity | none | none | NS | 13.4 |
| Fault detection gain | `experiment/FaultDetectionExperiment` (two-arm, shared mutant) | `FaultDetectionExperimentTest` (14 tests, docs/verification/2026-11-01-g8-focused-tests.txt) | VERIFIED (fault-detection gain, G8 PARTIAL) | 17 single-line V16 faults: manual-only 15/17=0.8824, manual+generated 16/17=0.9412, additional detection = 1 (`Calculator.negate`), regressions = 0, undetected in both = 1 (`CheckoutService.quote`); docs/verification/2026-11-01-g8-experiment.txt, experiments/fault-detection-report.md, experiments/fault-detection-results.json. Coverage before/after and supported-vs-skipped still NS |

## F. Scenario matrix (roadmap TS-01..TS-26) with evidence from the code

| ID | Scenario | Existing related tests | Status |
|---|---|---|---|
| TS-01 | One method body changed | `ExperimentRunnerTest`, `...TransitiveIntegrationTest` | NV |
| TS-02 | Methods in different classes changed | `ChangeImpactAnalyzerIntegrationTest` (legacy) | NV |
| TS-03 | Changed method no test reaches | `SelectTestsCliWarningTest`, `...ReportIntegrationTest` | NV |
| TS-04 | New method added | `LargerSampleHistoryTest`, `...TransitiveIntegrationTest` (V3->V4) | NV |
| TS-05 | Method deleted | `SelectTestsCliRemovedTest`, `...DeletedFileIntegrationTest` | NV (flagging of referencing tests missing) |
| TS-06 | Class header/annotation/field/ctor changed | `NonMethodChangeDetectorTest`, `ChangeImpactAnalyzerNonMethodChangeTest` | IP (partial; H4 covers records + annotation types; enum/nested-class headers still whole-file) |
| TS-07 | Several methods in one class | partly `LargerSampleHistoryTest` | IP |
| TS-08 | Many tests reach one method | `...OrderIntegrationTest`, `LargerSampleHistoryTest` | NV |
| TS-09 | No change / no tests affected | compare-to-self tests | NV |
| TS-10 | Call chain depth >= 3 | `LargerSampleHistoryTest` (V13->V14) | NV |
| TS-11 | Multiple commits spanned | `ExperimentRunnerTest` | NV |
| TS-12 | Selected test fails | `FaultInjectionRunnerTest` | NV |
| TS-13 | Invalid repo or commit | `SelectTestsCliTest`, `ExperimentCliTest` (exit 2) | NV |
| TS-14 | `pom.xml`/resource changed | `ChangedFileClassifierTest`, `ChangeImpactAnalyzerBuildResourceFallbackTest` | VERIFIED (H4: FALLBACK + select-all, 2026-11-01-mvn-test-h4.txt) |
| TS-15 | Unparseable source | none | NS |
| TS-16 | Overloaded method modified | `MemberAwareSelectionIntegrationTest` (different-arity overload not selected), `MemberChangeDetectorTest`, `MethodChangeDetectorSafetyTest` | IP (arity-aware, H3; same-arity overloads collapse) |
| TS-17 | Test file added/modified | `H7TestDiscoveryIntegrationTest` (`addedTestMethodIsSelectedUnconditionally`, `modifiedTestMethodIsSelectedUnconditionally`, `nonConventionallyNamedTestFileIsDiscoveredAndAssociated`) | VERIFIED (H7, 2026-11-01-mvn-test-h7.txt) |
| TS-18 | Change via `@BeforeEach` only | `H7TestDiscoveryIntegrationTest` (`changeReachableOnlyThroughBeforeEachSelectsTheClassTests`), `TestSourceProviderTest` | VERIFIED (H7, 2026-11-01-mvn-test-h7.txt) |
| TS-19..TS-26 | Generation scenarios (no tests, add, modify, remove, unsupported, location, determinism, uncompilable target) | none | NS |

## G. Traceability to gaps
FR2: GAP-01..04, 12, 20. FR3: 05, 06. FR4: 07, 24. FR5/FR6: 08-11, 13, 16. FR7: 14, 15, 16. FR9: 22. FR10-14 and O8: GAP-25. Backend/DB/UI: GAP-26. Tests env: GAP-18. Entry point: GAP-21.
H4 closes GAP-04 (build/resource changes → FALLBACK + select-all) and advances GAP-03 (record/annotation-type fingerprints, unexplained-change uncertainty trigger); verified by docs/verification/2026-11-01-mvn-test-h4.txt.
H2 closes GAP-18 (configurable sample path: system property `rts.sample` > env `RTS_SAMPLE_PROJECT` > default, with an actionable diagnostic for a missing/invalid path); verified by docs/verification/2026-11-01-mvn-test-h2.txt.
H7 closes GAP-08 (repo-wide test discovery instead of `Foo`->`FooTest` only), GAP-09 (lifecycle attribution), GAP-10 (added/modified tests selected unconditionally), GAP-11 (removed-method callers reported as potentially broken) and GAP-24 (parent links / explanation chain); it advances GAP-13 (nested types now use an enclosing-type identifier, but enum/record test containers are still not inspected, so GAP-13 stays partial); verified by docs/verification/2026-11-01-mvn-test-h7.txt (249 tests). GAP-06 (callee class UNKNOWN) stays OPEN, which also limits the explanation chain's terminal node.
H8 closes GAP-14 (`--execute` now runs in an isolated temporary checkout of the requested new revision via `IsolatedRevisionCheckout`, so stale working-tree sources can no longer be executed), GAP-15 (configurable `TestRunner` timeout with process-tree termination, reported distinctly as timed out and logged) and GAP-16 (per-test results from Surefire XML, with assertion failures distinguished from execution errors and compilation failures detected separately); verified by docs/verification/2026-11-01-h8-focused-tests.txt (24 focused tests) and docs/verification/2026-11-01-mvn-test-h8.txt (269 tests).
H10 closes GAP-17 (ground-truth precision/recall evaluation): new `eval` package establishes a reproducible evaluation framework; `FaultInjectionEvaluator` runs the experiment using fault-injection-established ground truth (failing tests at the mutant revision); 17-fault experiment at V16 produced TP=37, FP=18, FN=0, precision=0.6727 (lower bound, D-16), recall=1.0000 (exact); verified by docs/verification/2026-11-01-h10-focused-tests.txt (40 focused tests), docs/verification/2026-11-01-mvn-test-h10.txt (334 tests), and docs/verification/2026-11-01-h10-ground-truth-experiment.txt (experiment run). Remaining limitation: ground truth covers only single-line faults at V16; multi-line, logic-level, and cross-method faults are not yet evaluated.
