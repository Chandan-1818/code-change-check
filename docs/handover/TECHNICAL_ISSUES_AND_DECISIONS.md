# TECHNICAL_ISSUES_AND_DECISIONS.md

## 0. Source limits
I do **not** have the earlier Claude AI conversations. This history is reconstructed from: `CLAUDE.md`, synopsis section 24, the blueprint (decision log D-01..D-24, gap register), the roadmap (reconciliation log), Javadoc and test comments in the code. Entries marked `[INFERRED]` are deductions from code/test comments, not recorded statements. TRAE should ask the author about anything important that is missing.

## 1. Decisions (authoritative: blueprint section 21)

| ID | Decision | Rejected / alternatives |
|---|---|---|
| D-01 | Static call-graph test mapping; JaCoCo per-test coverage optional | Original blueprint: per-test JaCoCo |
| D-02 | Run tests in an **external Maven process** with timeout (timeout not yet implemented) | Maven Invoker (roadmap 1); JUnit Launcher in-process (roadmap 2) |
| D-03 | Custom fault-injection runner; PIT optional, state which was used | PIT as primary |
| D-04 | Impact depth default **unlimited**, configurable, truncation reported. **CONFIRMED by author 2026-11-01.** Implemented in H6: `ImpactAnalyzer.UNLIMITED_DEPTH = -1` is the default; a positive limit is allowed; 0 and negatives other than -1 are rejected; `ImpactSet.isTruncated()` and a CLI warning report truncation. Evidence: docs/verification/2026-11-01-mvn-test-h6.txt | 3 (original blueprint), 5 (roadmap 2/code) |
| D-05 | Renames = delete + add; limitation documented | Rename detection |
| D-06 | Removed methods: selection unchanged, referencing tests flagged "potentially broken". **IMPLEMENTED in H7**: `ChangeImpactAnalyzer` reports tests calling a method removed between the revisions via `TestSelectionReport.getPotentiallyBrokenTests` (CLI "Potentially broken tests" section) without changing impact-based selection. Evidence: docs/verification/2026-11-01-mvn-test-h7.txt | Select callers (roadmap 1); exclude (blueprint) |
| D-07 | New or modified tests always selected (required, unimplemented, GAP-10). **IMPLEMENTED in H7**: test sources at the new revision are compared with the old revision and added/modified test methods are selected unconditionally. Evidence: docs/verification/2026-11-01-mvn-test-h7.txt (`H7TestDiscoveryIntegrationTest`) | |
| D-08 | DB only for history across sessions; JPA + H2 default, PostgreSQL/MySQL by config | Mandatory DB |
| D-09 | Static HTML/JS + Bootstrap + Chart.js dashboard; React optional | React, Thymeleaf |
| D-10 | API path set in blueprint section 15 (`/api/v1/analysis/...`) | Two other path sets |
| D-11 | P1-P3 milestone mapping provisional; confirm with guide | |
| D-12 | Keep `com.analysis.tool` packages and the existing Maven module; no restructure | `com.project.rts`, `com.rts.analyzer`, `backend/`+`frontend/` monorepo |
| D-13 | Minimum 8 experiment pairs, 3 timing runs each | 5, 5-10 pairs |
| D-14 | Pipeline order per synopsis (generate after impact, before mapping); **implementation order: mapping before generator** | |
| D-15 | Completion percentages (41%, 34%) dropped | |
| D-16 | Recall ground truth = fault-induced failing tests; precision reported as lower bound. **CONFIRMED by author and implemented in H10 (2026-11-01).** For each fault, the full suite is run at the mutant revision; tests that actually fail are the required (ground-truth) set. Precision understates true value because a selected test that still passes may be genuinely relevant. Experiment: 17 faults at V16, precision=0.6727 (lower bound), recall=1.0000 (exact). Evidence: docs/verification/2026-11-01-h10-ground-truth-experiment.txt; experiments/ground-truth-report.md; experiments/ground-truth-results.json. | |
| D-17 | Merged 16-chapter report | |
| D-18 | Merged sample-project minimum profile | |
| D-19 | Java 17 (matches `pom.xml`) | Java 21 |
| D-20 | Synopsis section 12 "four layers" corrected to five | |
| D-21 | Dashboard eight screens (incl. Generated Tests) | seven |
| D-22 | Literature survey: at least five papers actually read | 4-5 / 5-8 |
| D-23 | Generated tests live outside Git (open question for the guide) | Branch in target |
| D-24 | Generate for all supported methods by default; `--only-untested` option | |
| D-25 | `--execute` runs in an **isolated temporary checkout of the requested new revision** (`IsolatedRevisionCheckout`), never in the caller's working tree; the clone is deleted after the run and logs/reports are copied out on failure. **IMPLEMENTED in H8** (GAP-14). Consequence: `SelectTestsCliExecuteTest` was migrated (not removed) from the V8 working-tree numbers (7 tests) to the requested V5 revision's real suite (5 tests, 2 selected, 60.0%), with the justification recorded in the test's Javadoc. Evidence: docs/verification/2026-11-01-h8-focused-tests.txt | Running Maven in the caller's tree (previous behaviour) |
| D-26 | `TestRunner` is bounded by a configurable timeout (default 300000 ms) that terminates the process tree and reports `timedOut` distinctly from a test failure; per-test outcomes come from Surefire XML, and a compilation failure is reported with zero tests. **IMPLEMENTED in H8** (GAP-15, GAP-16). Evidence: docs/verification/2026-11-01-h8-focused-tests.txt | No timeout (previous behaviour); console summary as the only result source |
| D-27 | Preflight validation runs **before** analysis and returns a structured outcome, not console strings: `preflight/Preflight.validate(path)` returns `preflight/PreflightResult` whose status (`SUPPORTED`/`REDUCED`/`UNSUPPORTED`) is **derived** from the rejection/reduction reason lists, so a caller cannot report SUPPORTED while holding a reason. Filesystem + regex only (independent of the analysis pipeline), no new dependency. **Refuse (UNSUPPORTED, CLI exit 2):** missing path / not a directory; no `.git` directory; `.git` is a file (linked worktree or submodule gitdir pointer, GAP-19); multi-module Maven (`<modules>`); JUnit 4 (`junit:junit`/`junit-vintage`, or `org.junit.*` sources). **Reduce (REDUCED, analysis still runs):** Spring, Mockito, Lombok, reflection — reported with the detected reason. A missing `pom.xml` is **not** a rejection (plain/temp Git repos stay SUPPORTED), so H2-H8 temp-repo tests are unaffected. An UNSUPPORTED result keeps the pinned `SelectTestsCliTest` contract by routing to exit 2 with a message containing `Analysis failed`. **IMPLEMENTED in H9** (GAP-19). Evidence: docs/verification/2026-11-01-h9-focused-tests.txt | Treating unsupported repositories as fully supported; aborting on any non-standard build file; returning only console text |

**Authority order:** synopsis sections 1-23 > blueprint > roadmap > `CLAUDE.md`. Never alter the synopsis title, objectives, scope or requirements without a separately listed, approved proposal.

## 2. Implementation decisions visible in code `[INFERRED unless stated]`

| Decision | Evidence |
|---|---|
| Pin commit hashes, never `HEAD~n` | Comments in almost every integration test |
| Sample project grows over time, so Maven-launching tests clone a pinned revision (`PinnedSampleCheckout`) | Class Javadoc; `ExperimentExecutorTest` comment "(5-2)/5 = 60% at V5, not the V8 working-tree figure" |
| Callee class recorded as `UNKNOWN`; ImpactAnalyzer matches by method name at **every hop** | `ImpactAnalyzer` / `DependencyGraph.getCallersByMethodName` Javadoc |
| One combined graph over production + test sources so test -> prod -> prod chains connect | `ChangeImpactAnalyzerTransitiveIntegrationTest` Javadoc |
| Overloads grouped per name, **any** overload change marks the name modified (over-select, never hide) | `MethodChangeDetector` Javadoc |
| Comments stripped before fingerprinting; body compared via pretty-printer | `JavaParserAnalyzer` Javadoc |
| Non-method change flagged only when something OLD is missing in NEW; pure additions ignored | `NonMethodChangeDetector` Javadoc |
| Flagged file => all its method names treated as changed (conservative) | `ChangeImpactAnalyzer.analyzeTransitively` |
| Missing old/new/test file reads as empty string so added/deleted files do not crash | `ChangeImpactAnalyzer`; DeletedFile tests |
| Auto-discovery uses union of files at old and new revisions so deleted and added sources are both seen | `SelectTestsCli.discoverMappings`; TempRepo tests |
| Untested warning printed instead of "no impacted tests found" when changes exist but nothing selected; a removal alone prints no WARNING | `SelectTestsCliWarningTest`, `SelectTestsCliRemovedTest` |
| `cmd /c mvn` on Windows | `TestRunner` Javadoc |
| Last Surefire summary line wins (run-wide total) | `TestRunner` Javadoc |
| Delete `target/` before every run so classes from another revision cannot leak | `ExperimentExecutor`, `FaultInjectionRunner` |
| Empty selection is not executed (empty `-Dtest` would mislead) | `ExperimentExecutor` Javadoc |
| Fault text must occur exactly once; classification CAUGHT/MISSED/NOT_DETECTED_BY_SUITE; precision is a conservative lower bound | `FaultInjectionRunner` |
| Time reduction asserted nowhere; printed as informational because Maven start-up dominates 7-test suite | `EvaluationMetricsIntegrationTest`, `SelectTestsCliExecuteTest` |
| CLI exit codes 0/1/2; warnings and fallbacks do not change them | `SelectTestsCli`, blueprint section 16 |
| Existing tests are never deleted or weakened; migration needs a written reason | `CLAUDE.md` section 3 |

## 3. Previous bugs and how they were handled `[INFERRED]`

| Problem | Resolution evidence | Status |
|---|---|---|
| Overloads overwrote each other (last-one-wins map) | `MethodChangeDetector` rewritten; `...SafetyTest` | RESOLVED IN CODE — NOT VERIFIED |
| Signature-only changes invisible | `MethodInfo.declaration` compared | RESOLVED IN CODE — NOT VERIFIED |
| Comment-only edit counted as modification | `parseWithoutComments` | RESOLVED IN CODE — NOT VERIFIED |
| Constant/field/import/ctor/header change produced "no change" | `NonMethodChangeDetector` + expansion | PARTIAL |
| Multi-hop chains broken at `UNKNOWN` boundary | name-based bridging in `ImpactAnalyzer` | WORKAROUND (name collisions possible) |
| Direct-call mapper missed `testCompute` (compute -> multiply) | Gap test documents it; transitive path fixes it | RESOLVED; gap test must stay |
| Crash when a mapped source file was deleted (Phase 9) | empty-string fallback; DeletedFile tests | RESOLVED IN CODE for the transitive path; legacy path still crashes |
| Removed method silently dropped | `removedMethods` in report and CLI | RESOLVED IN CODE |
| Mixed-in sample growth changed asserted numbers (7 tests at V8 vs 5 at V5) | `PinnedSampleCheckout`; per-revision checkout in `ExperimentExecutor` | RESOLVED for those tests; `--execute` still affected |
| Windows `mvn` not launching | `cmd /c` | RESOLVED |
| Time-reduction negative/unstable on tiny suites | documented caveat, not asserted | ACCEPTED LIMITATION |

## 4. Unresolved issues (beyond gap register)

New observations from reading, not in the register. **Confirm by running before treating as bugs.**

| ID | Observation | Risk |
|---|---|---|
| N-01 | Change set and BFS seeds are keyed by method **name** across all files and the whole graph. One changed `add` seeds every `add` node | Over-selection |
| N-02 | Calls inside constructors, initializers, field initializers, enum/record methods are not graph edges | Under-selection (U) |
| N-03 | Nested class methods are attributed to outer and inner class in both graph and test-node collection; `@Nested` ids not valid for `-Dtest` | Wrong identifiers / duplicates |
| N-04 | `getCallersByMethodName` scans all nodes per call, run for every hop of every changed method | Performance on larger projects |
| N-05 | `SelectTestsCli` accepts `--execute` only as last argument; `ExperimentCli` accepts flags anywhere | Inconsistent UX |
| N-06 | `discoverMappings` duplicated in `SelectTestsCli` and `ExperimentRunner` | Drift |
| N-07 | `EvaluationMetrics` returns 0.0 on zero denominators | Misleading (blueprint says "n/a"). **Note (H10):** the new `eval/PrecisionRecall` class correctly returns `null` (rendered `"n/a"`) for zero denominators; N-07 remains open only for the legacy `EvaluationMetrics` class (Phase 11). |
| N-08 | `SelectTestsCliExecuteTest` passes a V8 clone while comparing V4->V5; asserts 7 tests | Hides GAP-14; must be migrated when GAP-14 is fixed, with written justification |
| N-09 | Test files that are not `FooTest` never enter the graph | Selected tests missed |
| N-10 | Tests asserting the `MethodNode("UNKNOWN","multiply")` API will break if identity changes (H3) | Migration cost; allowed only with written reason |
| N-11 | `TestRunner` returns zero counts when no Surefire summary appears (e.g. compile error): indistinguishable from empty suite except `buildSucceeded=false` | Reporting |
| N-12 | JavaParser language level for Java 17 syntax unconfirmed | Parse failures on records, text blocks, switch expressions |

## 5. Open questions requiring the author or guide
1. Submission and review dates (no calendar possible).
2. University report format and P1-P3 requirements.
3. Depth default (D-04) and generation default scope (D-24).
4. Whether generated tests live outside Git (D-23).
5. Do Randoop/EvoSuite comparisons stay optional?
6. Surefire handling of fully qualified ids, `@Nested`, parameterized tests (unchecked).
7. Location of the eight handover files (suggest `docs/handover/`) and whether to archive the three old planning files via `git mv`.
8. Oracle-capture technique for the generator: compile with `javax.tools` vs `mvn compile` in a temp checkout, and how to obtain the dependency classpath offline (open design point, decide in G1).

## 6. Rules that must survive migration
- Never delete, disable or weaken a test to pass a build.
- Never invent results, counts, timings or hashes.
- Never mark planned work as completed; never claim selection is "safe".
- Never claim the tool works on arbitrary repositories; report REDUCED/FALLBACK confidence.
- Generator: fully local, deterministic, no external AI/cloud, separate JVM with timeout, tests stored outside the tool's `src/test/java` and outside target repository commits.
- No force-push, `reset --hard`, `clean -fd`, or branch deletion without explicit permission.
