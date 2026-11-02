# PROJECT_STATUS_REPORT.md

Status words (from `CLAUDE.md`): NOT STARTED, IN PROGRESS, IMPLEMENTED — NOT VERIFIED, VERIFIED, BLOCKED.
A component becomes VERIFIED only after the user runs the command and the output is saved to `docs/verification/<date>-<what>.txt` with command, commit hash and output tail.

## 1. Evidence basis and baseline

| Item | State |
|---|---|
| Evidence | Source and test files were read from the attached project files |
| `mvn` build, tests, `git status`, `git log`, branch, remote | **UNKNOWN. Never seen by the author of this handover** |
| Sample repo `D:/PROJECT/SAMPLE-PROJECT/sample-project` and 16 pinned hashes | **Not inspected** |
| Test count | 89 `@Test` methods in 35 classes, counted statically. **Pass/fail unknown** |
| **VERIFIED components** | **None** |
| Completion percentages | Deliberately not stated (decision D-15) |

First action for TRAE: establish the baseline (see `SETUP_AND_EXECUTION_GUIDE.md`, H1).

## 2. Component status

| Component | Status | Evidence | Known gaps |
|---|---|---|---|
| Git access (`GitAnalyzer`) | IMPLEMENTED — NOT VERIFIED | class + `GitAnalyzerTest`, `GitAnalyzerListFilesTest` | No rename detection; `.git` must be a directory (GAP-19) |
| Method extraction and method-name change detection | IN PROGRESS | `JavaParserAnalyzer`, `MethodChangeDetector`, `MethodChangeDetectorSafetyTest` | Name-level identity; constructors not detected as methods; no typed parse errors |
| Non-method change detection | IN PROGRESS | `NonMethodChangeDetector` (+ 18 tests), `ChangeImpactAnalyzerNonMethodChangeTest` | H4 adds record/annotation-type fingerprints and `ChangedFileClassifier`; enum/nested-class headers still via whole-file expansion; build/resource files handled by conservative FALLBACK |
| Dependency graph | IN PROGRESS | `DependencyGraph`, `DependencyGraphBuilder` | Name-based; callee class `UNKNOWN`; no constructor/initializer/inheritance/field edges; nested-class duplication |
| Impact analysis | IN PROGRESS | `ImpactAnalyzer`, `ImpactSet` | Depth cap 5 silent; name matching over-selects; O(N) caller scan |
| Test mapping and selection | IN PROGRESS | `ChangeImpactAnalyzer`, `FileMappingDiscoverer`, `TestSelectionReport` | `Foo`->`FooTest` only; `@Test` only; no lifecycle links; no test-file changes; change set keyed by name across files |
| Edge cases (untested, removed, deleted, new file) | IMPLEMENTED — NOT VERIFIED | DeletedFile, Removed, Warning, AutoDiscover(TempRepo) tests | Tests referencing removed methods not flagged |
| CLIs | IN PROGRESS | `SelectTestsCli`, `ExperimentCli` | `--execute` runs in given tree, last-arg only; duplicated `discoverMappings`; no packaged entry |
| Test execution | IN PROGRESS | `TestRunner` | No timeout; no per-test results; failures+errors merged; no compile-failure distinction |
| Reduction metrics | IMPLEMENTED — NOT VERIFIED | `EvaluationMetrics` | Time reduction unreliable on tiny suites; zero denominators return 0.0, not "n/a" |
| Precision/recall/fault detection | IN PROGRESS | `FaultInjectionRunner`, `GrownSampleFaults` | Proxy precision only (lower bound); no ground-truth relevant set |
| Multi-revision experiments | IN PROGRESS | `ExperimentRunner`, `ExperimentExecutor`, 3 test classes | **No results recorded anywhere** |
| Sample target project | IMPLEMENTED — NOT VERIFIED | Revisions V1-V16 referenced | Hard-coded Windows path; repo not inspected |
| Preflight, selection confidence, conservative fallback | NOT STARTED | no `preflight` package | Blueprint sections 10.3 and 14 |
| **Automatic test generation (MANDATORY)** | **NOT STARTED** | no `testgen` package; no class, test or dependency | Design only: blueprint section 12 |
| Spring Boot backend, DB, dashboard | NOT STARTED | `pom.xml` has no Spring/JPA; `App` stub | |
| Report, milestone decks, demo | NOT STARTED | | |

## 3. Findings preserved from the earlier audit

1. Automatic test generation has **not started**. Nothing in source, tests, `pom.xml` or `.gitignore` implements or prepares it. No repository evidence contradicts this.
2. The dependency graph is **name-based** (`MethodNode(class, method)`, callee class `UNKNOWN`, matching by method name). Confirmed in `DependencyGraphBuilder`, `ImpactAnalyzer`, `DependencyGraph.getCallersByMethodName`.
3. Several GAP items are **partially resolved** (table below).
4. The current documentation is **outdated** relative to the code (section 5).
5. The verification baseline is **unknown**.

## 4. Gap register status (blueprint section 19, updated from code reading)

"Closed" is never claimed without run evidence. Severity: U unsafe, O over-select, R reporting/operational, M missing mandatory.

| ID | Sev | Register says | Code reading shows | Status |
|---|---|---|---|---|
| GAP-01 overloads | U | Overloads overwrite | `MethodChangeDetector` groups all overloads per name. H3: `MemberChangeDetector` + `MemberId(className, memberName, arity, kind)` separate overloads by arity and the changed member's arity narrows the first impact hop (`MemberAwareSelectionIntegrationTest`). Still collapse when arities are equal (types unresolved) | PARTIALLY RESOLVED — arity-aware; same-arity overloads still collapse — VERIFIED by docs/verification/2026-11-01-mvn-test-h3.txt |
| GAP-02 signature-only | U | Not detected | `MethodInfo.declaration` compared; `signatureOnlyChangeIsDetected`; H3 adds arity to member identity | RESOLVED — VERIFIED (H3 evidence: docs/verification/2026-11-01-mvn-test-h3.txt) |
| GAP-03 non-method changes | U | Undetected | `NonMethodChangeDetector` + whole-file expansion; H4 adds record (header + components) and annotation-type fingerprints; a changed `.java` file whose change no member-level detector explains is an uncertainty trigger (whole-file expansion, REDUCED) instead of being silently ignored; CLI prints expanded files | PARTIALLY RESOLVED — class-level (records, annotation types) + unexplained-change trigger verified by docs/verification/2026-11-01-mvn-test-h4.txt; enum/nested-class headers still via whole-file expansion |
| GAP-04 build/resource changes | U | Ignored | H4 adds `ChangedFileClassifier` (BUILD/RESOURCE/OTHER categories); build (`pom.xml`, Gradle) and resource changes cannot be analysed at member level, so they force FALLBACK confidence and conservatively select every discovered test; CLI reports them | RESOLVED — conservative fallback, VERIFIED by docs/verification/2026-11-01-mvn-test-h4.txt (build/resource FALLBACK + select-all) |
| GAP-05 inheritance/override/field/ctor edges | U | None | H3 adds constructor declarations as nodes and `new X(...)`/`this(...)` as edges with a resolved class; field/constant changes reachable only via whole-file expansion (no field edges). Inheritance/override edges still none | PARTIALLY RESOLVED — constructor edges only — VERIFIED by docs/verification/2026-11-01-mvn-test-h3.txt |
| GAP-06 callee `UNKNOWN` | O | | Method callee class names are still recorded UNKNOWN; H3 does not add a Symbol Solver, so no type-based resolution | OPEN |
| GAP-07 depth cap 5 silent | U | | H6: `MAX_DEPTH` removed; depth configurable (`-1` = unlimited default, positive = max hops, 0/other negatives rejected); `ImpactSet.isTruncated()` + CLI truncation warning | CLOSED BY H6 (VERIFIED) — docs/verification/2026-11-01-mvn-test-h6.txt |
| GAP-08 only `Foo`->`FooTest`, only `@Test` | U | | H7: `TestSourceProvider` + `ChangeImpactAnalyzer` discover every `src/test/java/**.java` file via the call graph, so a non-conventionally named test file is associated without a naming convention; all five JUnit 5 test annotations accepted | RESOLVED — VERIFIED by docs/verification/2026-11-01-mvn-test-h7.txt (`H7TestDiscoveryIntegrationTest.nonConventionallyNamedTestFileIsDiscoveredAndAssociated`) |
| GAP-09 lifecycle methods | U | | H7: `@BeforeEach/@BeforeAll/@AfterEach/@AfterAll` attributed to their own class's tests and nested descendants, so a change reachable only through a lifecycle method selects the class's tests | RESOLVED — VERIFIED by docs/verification/2026-11-01-mvn-test-h7.txt (`changeReachableOnlyThroughBeforeEachSelectsTheClassTests`) |
| GAP-10 added/modified tests not selected | U | | H7: test sources at the new revision are compared with the old revision; added or modified test methods are selected unconditionally (D-07) | RESOLVED — VERIFIED by docs/verification/2026-11-01-mvn-test-h7.txt (`addedTestMethodIsSelectedUnconditionally`, `modifiedTestMethodIsSelectedUnconditionally`) |
| GAP-11 tests referencing removed methods | R | | H7: tests that call a method removed between the revisions are reported as potentially broken (`TestSelectionReport.getPotentiallyBrokenTests`, CLI "Potentially broken tests" section) without changing impact-based selection | RESOLVED — VERIFIED by docs/verification/2026-11-01-mvn-test-h7.txt (`testsCallingARemovedMethodAreReportedPotentiallyBroken`) |
| GAP-12 unparseable source, Java 17 level | U | | H5: `SourceParseException` (typed), JavaParser `LanguageLevel.JAVA_17`, FALLBACK selects every discovered test; no crash | CLOSED BY H5 (VERIFIED) — docs/verification/2026-11-01-mvn-test-h5.txt |
| GAP-13 nested/enum/record | U | | H7: a `@Nested` test keeps the enclosing-type identifier (`Outer$Inner#method`) and is no longer double-attributed to the enclosing class; lifecycle inside a nested class is attributed to that class only. Enum/record test containers are still not inspected | PARTIALLY RESOLVED — nested types handled — VERIFIED by docs/verification/2026-11-01-mvn-test-h7.txt (`TestSourceProviderTest.nestedTestKeepsEnclosingIdentifierAndIsNotDoubleAttributed`) |
| GAP-14 `--execute` working tree | R | | H8: `execution/IsolatedRevisionCheckout` clones the repository into a temp directory and checks out the requested new revision; `SelectTestsCli --execute` runs Maven there, never in the caller's tree, and copies the log/reports out on failure | CLOSED BY H8 (VERIFIED) — docs/verification/2026-11-01-h8-focused-tests.txt (`IsolatedRevisionCheckoutTest`, migrated `SelectTestsCliExecuteTest`) |
| GAP-15 `TestRunner` timeout missing | R | | H8: configurable timeout (`--timeout=<ms>`, default 300000 ms); an over-budget run has its whole process tree terminated and is reported as `timedOut`, distinctly from a test failure, with `target/maven-execution.log` preserved | CLOSED BY H8 (VERIFIED) — docs/verification/2026-11-01-h8-focused-tests.txt (`TestRunnerExecutionHardeningTest.timesOutAndTerminatesInsteadOfHanging`) |
| GAP-16 identifiers by simple name / result detail | R | | H8: `execution/SurefireReportParser` reads the per-test Surefire XML, so passed/failed (assertion)/errored (execution)/skipped are reported individually and a failure is distinguished from an error; malformed/missing reports are tolerated and no per-test detail is invented; compilation failures are detected separately | CLOSED BY H8 (VERIFIED) — docs/verification/2026-11-01-h8-focused-tests.txt (`SurefireReportParserTest`, `TestRunnerParseResultTest`, `TestRunnerExecutionHardeningTest.distinguishesAssertionFailuresFromExecutionErrors`) |
| GAP-17 no ground truth | R | | Fault injection is proxy only | OPEN |
| GAP-18 hard-coded `D:/` path | R | | Sample path read from `rts.sample` / `RTS_SAMPLE_PROJECT` with a default (`SamplePaths`) | CLOSED (VERIFIED) — SamplePathsTest in docs/verification/2026-11-01-mvn-test-h5.txt |
| GAP-19 `.git` must be a dir | R | | H9: `preflight/Preflight` inspects the repository before analysis. A `.git` **directory** is required; a missing `.git` or a `.git` **file** (linked worktree / submodule gitdir pointer) is refused as UNSUPPORTED with an actionable reason (no `.git` directory under … / unsupported Git layout … is a file …), and the CLI exits 2. The same preflight also refuses a multi-module Maven project (`<modules>`) and a JUnit 4 project (`junit:junit`/`junit-vintage` dependency or `org.junit.*` source usage), and reports REDUCED confidence (analysis still runs) for Spring/Mockito/Lombok/reflection usage that can hide call edges | CLOSED BY H9 (VERIFIED) — docs/verification/2026-11-01-h9-focused-tests.txt (`PreflightTest`, `PreflightResultTest`, `PreflightCliIntegrationTest`) and docs/verification/2026-11-01-mvn-test-h9.txt (294 tests) |
| GAP-20 comment-only change | O | "unverified" | `parseWithoutComments` strips comments; `commentAndWhitespaceOnlyChangeIsNotAChange` and `commentOnlyChangeSelectsNothing` | VERIFIED — both tests pass in docs/verification/2026-11-01-mvn-test-h5.txt |
| GAP-21 no entry point | R | | Confirmed | OPEN |
| GAP-22 nothing verified | R | | Confirmed | OPEN |
| GAP-23 legacy path crashes on deleted source | R | | Confirmed (`findImpactedTestsForFile`) | OPEN |
| GAP-24 no explanation chain | R | | H7: `ImpactSet` carries a `parents` map and `explanationChain(MethodNode)` (cycle-safe, nearest-first); `TestSelectionReport.getExplanations` and the CLI "Selection reasons" section print the chain. The terminal node is rendered from the changed member's identity while callee classes remain UNKNOWN (GAP-06) | RESOLVED — VERIFIED by docs/verification/2026-11-01-mvn-test-h7.txt (`ImpactSetExplanationChainTest`, `H7TestDiscoveryIntegrationTest`) |
| GAP-25 test generation | M | | None | OPEN |
| GAP-26 backend/DB/dashboard | M | | None | OPEN |

## 5. Documentation vs code discrepancies

| # | Document statement | Code evidence |
|---|---|---|
| 1 | Synopsis section 24, blueprint, roadmap: overloads collapse; signature-only undetected; fields/constants/constructors/class headers undetected | Name-level and `NonMethodChangeDetector` detection; H3 adds arity-aware member identity and precise constructor change detection/selection (same-arity overloads still collapse; see GAP-01/02/03/05) |
| 2 | Blueprint package map lacks `NonMethodChangeDetector` | Class exists in `impact` |
| 3 | Roadmap TS-06 and TS-16 existing tests "none" | Partial coverage exists (`NonMethodChangeDetectorTest`, `ChangeImpactAnalyzerNonMethodChangeTest`, `MethodChangeDetectorSafetyTest`) |
| 4 | Blueprint: whole **class** marked changed | Code expands the whole **file** (all method names in the file) |
| 5 | Blueprint 10.1: result lists expanded files | `TestSelectionReport` has the field; CLI never prints it |
| 6 | Blueprint 13.1: zero denominators "n/a" | `EvaluationMetrics` returns 0.0 |
| 7 | `CLAUDE.md`: add `generated-tests/` to `.gitignore` | Not yet added |
| 8 | `ChangeImpactAnalyzer` Javadoc | Stale header; garbled sentence on name collisions |
| 9 | Handover prompt lists eight files | They did not exist; this handover creates them |
| 10 | Blueprint 11: `--execute` must use temp checkout | Only `ExperimentExecutor` does |

## 6. Test portability summary (static)
- Pure or temp-repo (portable, no Windows path): ~34 methods.
- Need the sample git repo at `D:/PROJECT/SAMPLE-PROJECT/sample-project` (fast, Git only): ~40 methods.
- Launch real Maven subprocesses (slow): in `TestRunnerIntegrationTest`, `EvaluationMetricsIntegrationTest`, `SelectTestsCliExecuteTest`, `ExperimentExecutorTest`, `ExperimentCliExecuteTest`, `FaultInjectionRunnerTest`, `FaultInjectionGrownSampleTest`.
(Approximate classification by reading; recompute after the baseline.)

## 7. Remaining work and effort (planning figures, not measured)
8A about 14 days; 8B about 22; Phases 13-16 about 18-24; 17-19 about 13-17; 20-23 about 9-13. Total roughly 76-90 working days. No calendar can be set: submission/review dates and university format/milestone rules are not recorded.

## 8. Recommended next task
**R0/H1: verification baseline** (no source change). Then R1 documentation reconciliation, then H5, H6 and so on per `PROJECT_ROADMAP.md`.
