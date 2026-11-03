# PROJECT_ROADMAP.md

Derived from `PROJECT_ROAD_MAP_UPDATED.md` (v2.0, 2026-10-25), adjusted for what the code shows. Phase numbers 0-23, 8A, 8B are preserved. Estimates are planning figures from the roadmap, not measured velocity. No dates can be set. **Nothing is VERIFIED.**

Common rules for every task: read code and tests first; one logical unit at a time; add or update tests with every change; never delete, disable or weaken a test (migration needs a written reason); run the relevant tests, then the fast suite, then the slow classes before calling a feature complete; record evidence in `docs/verification/`; commit only that step's files.

## 1. Implementation order

```
R0 baseline (H1) -> R1 doc reconciliation
 -> H5 confidence/fallback + typed parse errors -> H6 depth flag
 -> H3 identity -> H4 class/build/resource triggers -> H2 configurable sample path
 -> H7 test discovery -> H8 execution hardening -> H9 preflight -> H10 ground truth
 -> 8B (G1..G8) test generation -> 9 -> 10 -> 11 -> 12
 -> 13 -> 14 -> 15 -> 16 -> 17 -> 18 -> 19 -> 20 -> 21 (checkpoints) -> 22 -> 23
```
Rationale: baseline first (nothing else is trustworthy); safety gaps (U) before the generator because generated tests flow through selection; the generator is the largest risk, so it starts as soon as selection is hardened. Do **not** cut 8B or the evaluation. Scope cuts if time is short: DB optional, static dashboard, drop Randoop/EvoSuite comparison and second sample project.

## 2. Status of phases 0-12 (existing work)
| Phase | Status | Note |
|---|---|---|
| 0 Understand | NOT STARTED (not evidenced) | notes + diagram deliverable |
| 1 Environment | IMPLEMENTED — NOT VERIFIED | `pom.xml` targets Java 17 |
| 2 Target project | IMPLEMENTED — NOT VERIFIED | V1-V16 referenced; repo not inspected |
| 3 Git | IMPLEMENTED — NOT VERIFIED | |
| 4 Change detection | IN PROGRESS | partly ahead of docs |
| 5 Parsing | IN PROGRESS | Java 17 level unconfirmed |
| 6 Graph | IN PROGRESS | name-based |
| 7 Impact | IN PROGRESS | cap 5 |
| 8 Mapping | IN PROGRESS | |
| 8A | NOT STARTED | below |
| 8B | NOT STARTED | below |
| 9 Selection | IN PROGRESS | |
| 10 Execution | IN PROGRESS | |
| 11 Metrics | IN PROGRESS | |
| 12 Integration | IN PROGRESS | |
| 13-23 | NOT STARTED (17, 18 partly: runners and tests exist, no recorded data) | |

## 3. Remaining tasks in order

### R0 / H1 — Verification baseline (0.5 d; closes GAP-22)
- **Depends on:** nothing. **Changes source:** no.
- **Files:** `docs/verification/<date>-baseline-fast.txt`, `...-baseline-slow-<Class>.txt`, `docs/verification/README.md` (index).
- **Tests:** full fast run, then each slow class alone.
- **Acceptance:** each file contains the command, `git rev-parse --short HEAD`, the date, the sample project `git log --oneline` and the output tail; failures classified as environment (sample repo, hash, `mvn` PATH, Windows path) vs code defect. No test changed.
- **Also:** add `generated-tests/` to `.gitignore` (separate tiny commit).

### R1 — Documentation reconciliation (0.5 d)
- Update blueprint package map (`NonMethodChangeDetector`), gap register (GAP-01/02/03/20 partial), roadmap TS-06/TS-16 evidence, synopsis section 24 table (only section 24, never sections 1-23). Record conflicts in the decision log. Archive the three old planning files with `git mv` into `docs/archive/` only if they exist and the user approves.
- **Acceptance:** every status change cites run evidence from R0.

### Phase 8A — Core hardening (about 14 d)
| Task | Days | Closes | Expected files | Tests required |
|---|---|---|---|---|
| H5 Selection confidence and conservative fallback; typed parse errors; Java 17 language level | 1 | GAP-12 | `impact/SelectionConfidence.java`, `impact/ParseFailure*.java` (or similar); extend `TestSelectionReport`; CLI output lines; `StaticJavaParser` configuration | Parse failure gives FALLBACK without crash; record/text-block/switch-expression source parses; HIGH/REDUCED/FALLBACK triggers; existing CLI tests still assert no `WARNING` where they do now |
| H6 Configurable depth with truncation flag | 1 | GAP-07 | `ImpactAnalyzer` (option), `ImpactSet` (truncated flag) | Four-method chain returns all four; cycle terminates; cap reports truncation; default unlimited (confirm D-04) — **DONE 2026-11-01, VERIFIED: docs/verification/2026-11-01-mvn-test-h6.txt** |
| H3 Stage A identity: member ids, arity-aware matching, constructors | 3 | GAP-01 (partial, arity), GAP-02, GAP-05 (partial, constructor edges) | new `MemberId` class alongside `MethodNode`; `DependencyGraphBuilder` constructor edges; `MemberChangeDetector`; `ImpactAnalyzer.analyzeMemberImpact`; `ChangeImpactAnalyzer` member-id selection | Overload edit (different arity) selects only the matching overload's tests; signature edit; constant edit; constructor edit detected and selected precisely. **DONE 2026-11-01, VERIFIED: docs/verification/2026-11-01-mvn-test-h3.txt (207 tests).** `MethodNode` identity unchanged, so no `MethodNode("UNKNOWN", ...)` test needed migration. Not resolved: same-arity/different-type overloads collapse (GAP-01 partial); callee class names remain UNKNOWN (GAP-06 stays OPEN, H3 adds no type resolution). GAP-20 and GAP-12 were already closed by H5, not by H3 |
| H4 Class-level and non-method triggers: records, annotation types, build/resource/unparseable file triggers | 2 | GAP-03 (partial), GAP-04 (partial) | extend `NonMethodChangeDetector`; `impact/ChangedFileClassifier.java`; `ChangeImpactAnalyzer` changed-files overload; `TestSelectionReport.buildOrResourceChanges`; wired into `ExperimentRunner` and `SelectTestsCli` | `pom.xml`/resource change gives FALLBACK; changed `.java` with no member change is an uncertainty trigger. **DONE 2026-11-01, VERIFIED: docs/verification/2026-11-01-mvn-test-h4.txt (225 tests).** Records and annotation types fingerprinted; build/resource/other changed files force FALLBACK and select every discovered test; an unexplained Java change (e.g. an added import) expands the whole file (REDUCED). Still not resolved: class/record *header* triggers are only detected when an existing fingerprint disappears (pure additions are handled by member detection or by the uncertainty trigger); build/resource changes are not analysed at all, only conservatively escalated (GAP-04 stays partial) |
| H2 Configurable sample path (system property/env, default current path) | 1 | GAP-18 | `testsupport/SamplePaths` | Tests pass unchanged with default; with another path set they use it. **DONE 2026-11-01, VERIFIED: docs/verification/2026-11-01-mvn-test-h2.txt (229 tests).** Resolution order `rts.sample` > `RTS_SAMPLE_PROJECT` > default preserved; `SamplePaths.sampleProject()` validates the directory and fails with an actionable diagnostic; path handling is `Path`-based/portable; the two negative CLI tests no longer hard-code a Windows `D:/...` path. Test support only, separate from the user-supplied target-repository path |
| H7 Test discovery: all test files, `@ParameterizedTest/@RepeatedTest/@TestFactory`, lifecycle attribution, `@Nested`, added/modified tests selected, potentially broken tests, explanation chain | 2 | GAP-08, 09, 10, 11, 24 (all closed), GAP-13 (partial) | `impact/TestSourceProvider.java`; parents in `ImpactSet` | Change reachable only via `@BeforeEach` selects the class's tests; every test method mapped or reported unmapped; added test selected unconditionally. **DONE 2026-11-01, VERIFIED: docs/verification/2026-11-01-mvn-test-h7.txt (249 tests).** `TestSourceProvider` discovers every `src/test/java/**.java` file and recognises all five JUnit 5 test annotations plus the four lifecycle annotations (nested types keep the `Outer$Inner#method` identifier; a lifecycle method is attributed to its own class and its nested descendants). `ChangeImpactAnalyzer` discovers test sources repo-wide instead of only `Foo`->`FooTest` pairs (GAP-08); added/modified test methods are selected unconditionally (GAP-10); tests calling a method removed between the revisions are reported as potentially broken (GAP-11); `ImpactSet` carries a `parents` map and `explanationChain`, surfaced by `SelectTestsCli` "Selection reasons" (GAP-24). Still not resolved: enum/record test containers are not inspected (GAP-13 partial); callee class names remain UNKNOWN, so a chain's terminal node is rendered from the changed member's identity (GAP-06 stays OPEN). Manual-test discovery only — the Phase 8B generated-test provider (G7) is unchanged |
| H8 Execution: temp checkout of new revision for `--execute`, timeout, per-test Surefire XML results, failure vs error, compile-failure detection | 1.5 | GAP-14, 15, 16 | `execution/*` changes, reuse XML reader from `FaultInjectionRunner` | Hanging run aborted; failing test reported; run happens at new revision. **DONE 2026-11-01, VERIFIED: docs/verification/2026-11-01-mvn-test-h8.txt (269 tests) and docs/verification/2026-11-01-h8-focused-tests.txt (24 focused tests).** `execution/IsolatedRevisionCheckout` clones the repository into a temporary directory and checks out the requested new revision; `SelectTestsCli --execute` runs Maven there, so the caller's working tree is untouched and stale sources cannot be executed (GAP-14); on failure the log and Surefire reports are copied to a preserved directory whose path is printed. `TestRunner` takes a configurable timeout (`--timeout=<ms>`, default 300000 ms), terminates the whole process tree on expiry and reports `timedOut` distinctly, keeping `target/maven-execution.log` (GAP-15). `execution/SurefireReportParser` reads the Surefire XML so the summary names passed/failed/errored/skipped tests and distinguishes an assertion failure from an execution error; malformed/missing reports are tolerated and no per-test detail is invented (GAP-16). `"COMPILATION ERROR"` is detected and reported as a compilation failure with zero tests. The pinned `SelectTestsCliExecuteTest` was **migrated, not removed**: it now asserts the new summary labels and V5's real suite (5 tests, 2 selected, 60.0% reduction) because execution moved from the caller's V8 working tree to the requested V5 revision — the GAP-14 hazard it previously (accidentally) worked around. Justification is recorded in the test's Javadoc. GAP-17 closed by H10 |
| H9 Preflight module | 1 | GAP-19 | `preflight/Preflight.java`, `PreflightResult.java` | Refuse multi-module/JUnit4/no-`.git`-dir; REDUCED for Spring/Mockito/Lombok/reflection indicators; worktree `.git` file handled or refused. **DONE 2026-11-01, VERIFIED: docs/verification/2026-11-01-mvn-test-h9.txt (294 tests) and docs/verification/2026-11-01-h9-focused-tests.txt (25 focused tests).** New `preflight/Preflight` inspects the target repository on the filesystem before any analysis and returns a structured `preflight/PreflightResult` (status `SUPPORTED`/`REDUCED`/`UNSUPPORTED` with the exact reasons, never console strings only). Rejections (UNSUPPORTED, CLI exit 2, message `Analysis failed: preflight validation refused this repository.` plus one line per reason): missing `.git` directory; a `.git` that is a **file** (linked worktree or submodule gitdir pointer, GAP-19); multi-module Maven (`<modules>` in `pom.xml`); JUnit 4 dependency (`junit:junit`/`junit-vintage`) or `org.junit.*` source usage. Reductions (REDUCED, printed as a `Preflight` block after `Confidence`, analysis still runs): Spring (`org.springframework`), Mockito (`org.mockito`), Lombok, and reflection (`java.lang.reflect`, `Class.forName`, `getDeclared*`). A missing `pom.xml` is not a rejection (plain/temp Git repos stay SUPPORTED). Integrated into `SelectTestsCli` before the analysis `try` block; existing public signatures and H2-H8 behaviour unchanged |
| H10 Ground-truth precision/recall evaluation | 1 | GAP-17 | `eval/` package (6 classes), `experiment/AnalysisTestSelector.java`, `experiment/FaultInjectionEvaluator.java`, `experiments/ground-truth-report.md`, `experiments/ground-truth-results.json` | **DONE 2026-11-01, VERIFIED: docs/verification/2026-11-01-mvn-test-h10.txt (334 tests), docs/verification/2026-11-01-h10-focused-tests.txt (40 focused tests), docs/verification/2026-11-01-h10-ground-truth-experiment.txt (17-case experiment).** New `eval` package: `GroundTruthCase` (validated record), `GroundTruthDataset` (pipe-delimited loader with per-line error reporting), `GroundTruthEvaluator` (dataset-level driver with `TestSelector` abstraction), `PrecisionRecall` (TP/FP/FN confusion-matrix arithmetic; zero denominators return `null` rendered as `"n/a"`, never silently 100%), `CaseResult` (per-case metrics with TP/FP/FN test sets), `EvaluationReport` (deterministic Markdown + JSON output). Production wiring: `AnalysisTestSelector` implements `TestSelector` using the real `ChangeImpactAnalyzer`; `FaultInjectionEvaluator.main()` is the reproducible CLI (`FaultInjectionEvaluator <repoPath> <baseRevision> [grown] [outputDir]`). Ground truth: for each fault, the full suite is run at the mutant revision; failing tests are the required set (D-16). Experiment result (17 faults at V16=7059f4c, grown-sample faults): TP=37, FP=18, FN=0; precision=0.6727 (lower bound, D-16), recall=1.0000 (exact — no required test was missed). Two cases (`Calculator.negate`, `CheckoutService.quote`) had no failing tests and were not selected: precision and recall are `n/a` for those. Dataset limitation: single-line faults only; multi-line, logic-level, or cross-method faults are not yet covered. GAP-17 CLOSED. |
- **Phase acceptance:** `mvn test` recorded with commit hash; every U-severity gap closed or surfaced as REDUCED/FALLBACK; scenarios TS-01..TS-18 each have a test and recorded result; stage B (Symbol Solver, dispatch edges) delivered or documented as a limitation.

### Phase 8B — Automatic test generation (MANDATORY, local; about 22 d)
**Status NOT STARTED. Design: blueprint section 12.** Depends on 4, 5, 6, 8, 8A (H3 identity and H7 `TestSourceProvider` especially).

| Task | Days | Expected files (proposed names, do not exist) | Notes |
|---|---|---|---|
| G1 Scope spec: supported types, skip reason codes, case cap 12, seed 42, output layout, **oracle-capture technique decision** | 1 | `docs/testgen-spec.md`; `testgen/SkipReason.java` | Decide `javax.tools` vs `mvn compile` in a temp checkout; how to get the dependency classpath offline |
| G2 Target discovery with supported/skipped classification | 2 | `testgen/TargetDiscoverer.java`, `TargetMethod.java` | Public methods of concrete non-generic classes; params primitive/boxed/String; abstract, generics, arrays, collections, varargs, time/random/thread/file/network/console use are skipped with a reason |
| G3 Seeded input generator and receiver construction | 3 | `testgen/InputPool.java`, `ReceiverFactory.java` | Boundary pools; all-base case, one-at-a-time variation, seeded fill; recursive construction with depth limit and cycle detection |
| G4 Oracle capture | 4 | `testgen/OracleHarness.java`, `OracleRecorder.java` | Separate JVM, per-method and overall timeout, output cap, record return or exception class, **repeat in a second JVM**, drop differing cases; not a security sandbox |
| G5 JUnit 5 emitter and manifest | 3 | `testgen/JUnitEmitter.java`, `GeneratedManifest.java` | `<Class>GeneratedTest`, package `rts.generated`, methods `gen_<Class>_<method>_<nn>`; `manifest.json` per blueprint 12.7; byte-identical on re-run |
| G6 Incremental update | 3 | `testgen/IncrementalUpdater.java`, accept command | Added: generate; modified: re-run, `BEHAVIOUR_CHANGED`, regenerate only after accept; removed: retire; unchanged: keep; never overwrite manual tests or oracles without accept |
| G7 Integration | 4 | `TestGenCli`; hooks in `TestSourceProvider`, `TestRunner`, orchestrator | Generated tests join graph and selection; copied into `src/test/java/rts/generated/` of the **temp checkout only**; reported separately from manual tests; skipped methods stay in the untested report |
| G8 Generator tests and first evaluation | 2 | `src/test/java/com/analysis/tool/testgen/*Test.java`; `experiment/FaultDetectionExperiment.java` | Generator's own tests live in the tool's tree; generated output lives in `generated-tests/<project>/<revision>/` (git-ignored). Fault-detection gain DONE (2026-11-01): two-arm experiment over 17 single-line V16 faults — manual-only mutation score 15/17=0.8824 vs manual+generated 16/17=0.9412, additional detection = 1 (`Calculator.negate`), regressions = 0, undetected in both = 1 (`CheckoutService.quote`); see `experiments/fault-detection-report.md`. Coverage before/after and supported-vs-skipped still NOT STARTED. |
- **Acceptance (all required):** (1) a project with no tests gets an initial suite that compiles and passes at its baseline revision; (2) two generations of one revision are byte-identical; (3) incremental update correct on at least three commit pairs; (4) every skipped method has a reason; (5) generated tests never appear in the tool's `src/test/java` or a target repository commit; (6) generated tests flow through mapping, selection, execution, reported separately; (7) no external AI/remote/cloud dependency (check `pom.xml` and code); (8) measured results recorded: coverage before/after, supported vs skipped, validity, fault-detection gain.
- **Test commands to provide:** initial generation on a no-test project (use a temp repo test fixture); change detection; regeneration after add, modify, remove; execution of generated tests; edge cases: unsupported types, nondeterminism, uncompilable target, timeout. First fault-gain candidates: `Calculator.negate`, `CheckoutService.quote` (known untested).
- **Pitfalls:** assertions are characterization, not proof; never write generated tests into the tool's test tree; unique names (the graph matches by name); drop flaky cases; claim nothing before running.

### Phase 9 — Selection completion (3-4 d)
Union of manual and generated tests; unconditional selection of added/modified tests; potentially broken tests; confidence/fallback; build-file full-suite fallback; zero-selection message distinct from untested warning.
- **Files:** `ChangeImpactAnalyzer`, `TestSelectionReport`, `SelectTestsCli`.
- **Acceptance:** no duplicates; three revision pairs equal manual expectation; "no impacted tests" and "untested change" behave per blueprint 10.2; skipped generation targets stay listed with reasons.

### Phase 10 — Execution (5-6 d)
Covered by H8 plus generated-test separation. **Acceptance:** two selected tests run and report shows exactly two; full counts match a manual `mvn test`; hang aborted by timeout; failing test FAILED; run is in a checkout of the new revision; manual and generated results separate.

### Phase 11 — Metrics (2 d)
`EvaluationMetrics` to return "n/a" on zero denominators; add synthetic test (100 tests, 20 selected, 50 s, 10 s gives 80% and 80%); generator metrics fields blank until measured; precision stated as lower bound.

### Phase 12 — Integration (4-5 d)
Orchestrator: preflight, generate/update, select, execute, report with confidence. Packaged entry point (decide shade/exec plugin; this changes `pom.xml`, so get approval). **Acceptance:** end-to-end on at least three commit pairs without manual intervention; errors surfaced not swallowed.

### Phases 13-16 — Backend, DB, dashboard, integration (18-24 d)
- **13 Spring Boot 3.x** wrapper (adds dependencies: needs approval), API per blueprint 15 including generation endpoints; async jobs; global exception handler. Acceptance: JSON and 200/400/500 correct; results equal CLI for same input; long jobs do not block.
- **14 DB (optional):** JPA + H2, others by config; or documented decision for no DB. Acceptance: runs persist across restarts, or the decision is recorded.
- **15 Dashboard:** static HTML/JS, Bootstrap, Chart.js, eight screens; real backend data, no mocks.
- **16 Integration:** input validation, timeouts, three consecutive runs without restart, no state leakage (including generated tests), no orphan Maven processes.

### Phases 17-19 — System testing, experiments, results (13-17 d)
- **17** Execute scenarios TS-01..TS-26 with documented expected/actual outcome (matrix in roadmap section 3).
- **18** At least 8 commit pairs (V1-V16 gives 15 consecutive), three timing runs each, with and without generated tests, fault-detection on at least three pairs, compatibility study on at least five unseen repositories (agree set size with guide). Store raw data, hashes, commands.
- **19** Averages, tables, charts, honest interpretation including negative results; two metrics recomputed by hand.

### Phases 20-23 — Report, milestones, demo, submission (9-13 d)
Report (16 merged chapters, at least five papers actually read, generation described as local characterization testing); P1-P3 provisional mapping, confirm with guide; timed 10-minute demo including a no-tests project; submission checklist (no secrets in repo).

## 4. Dependencies at a glance
8A needs 4-8; 8B needs 8A; 9 needs 7, 8, 8B; 10 needs 9; 12 needs 4-11; 13 needs 12; 15 needs 13; 16 needs 15; 17-19 need 16; 20 needs 0-19; 22 needs 16, 19, 21; 23 needs 22.

## 5. Confirm with the guide before building
P1-P3 definitions, report format, dates, generated tests outside Git, depth default, scope of generation, how many unseen repositories.
