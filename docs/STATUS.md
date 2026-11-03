# Implementation status (evidence-based)

Latest (2026-11-03, branch feature/complete-live-demo, HEAD 3c15535): **M0 — repository audit and
baseline verification (MASTER prompt M0–M10)**. Read-only audit; no production code was modified.
Verified against the actual repository: branch `feature/complete-live-demo`, HEAD `3c15535`
"feat: improve test generation and Maven classpath resolution" (2026-11-03), remote
`https://github.com/Chandan-1818/code-change-check.git`, working tree clean and up to date with origin
(the MASTER prompt's recorded `5e3d978` is superseded). Baseline `mvn -B clean test` on JDK 23.0.2
(compiled to target 17): 407 run, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS, 9:20 min — matches the
historical 407, so the newest STATUS.md entry below (403) was stale. Evidence:
docs/verification/2026-11-03-m0-baseline-mvn-test.txt. Audit findings by area (concrete file:line
evidence): **(A) method/member impact** — IMPLEMENTED: constructors, added-method and deleted-method
detection; PARTIAL: member identity (`className+memberName+arity+kind`, no parameter types),
cross-class calls (callee class always `UNKNOWN` in `DependencyGraphBuilder`), class-level impact
(whole-file expansion), static methods (no static/instance distinction), unresolved handling
(`UNKNOWN` + name/arity fallback); NOT IMPLEMENTED: same-arity overload resolution by parameter type,
inheritance, overrides, interface implementations, static imports, method references, renamed-method
detection. Root cause: `javaparser-symbol-solver-core` is a declared dependency (pom.xml) but is not
used anywhere. **(B) dependency graph / selection** — 12 of 16 capabilities IMPLEMENTED (graph build,
method-to-method mapping, constructor deps, test-method deps, direct/transitive impact, configurable
depth + truncation, changed-method→test mapping, explanations, dependency paths, conservative FALLBACK,
added/modified/deleted/renamed files, build/resource handling); PARTIAL: explicit unresolved-dependency
reporting; NOT IMPLEMENTED: explicit class-to-class map and a test-class (as opposed to test-method)
dependency concept. Recorded ground truth (17 cases) TP=37/FP=18/FN=0, precision=0.6727, recall=1.0000,
produced only by the standalone `eval` package (the production pipeline reports reduction % and
confidence only). **(C) G1–G7** — all IMPLEMENTED with file evidence; `MavenClasspathResolver` is wired
into `OracleCapture` via `GenerateTestsCli`→`TestGenerator`→`OracleCapture`. Correction to the status
below: generated tests DO already flow through the same `TestSourceProvider` (`ChangeImpactAnalyzer`
records `extraTestSources` and calls `testSourceProvider.discover(...)`), so the "TestSourceProvider
(generated tests in selection) NOT STARTED" row is stale. Remaining real gap: `NoExistingTestsProofTest`
covers generate→compile→execute but not regenerate/incremental or retire, so the mandatory
"no-existing-tests" end-to-end requirement is PARTIAL. **(D) G8** — 4 of 17 metrics IMPLEMENTED (fault
detection before/after, additional detections, regressions): manual-only 15/17=0.8824, manual+generated
16/17=0.9412, additional=1 (`Calculator.negate`), regressions=0, undetected-in-both=1
(`CheckoutService.quote`); 1 PARTIAL (total test count); 12 NOT IMPLEMENTED/NOT STARTED (existing and
generated test counts, coverage before/after/improvement — `StaticCoverage` is not wired into any G8
report, generated-test compilation success rate, execution pass rate, supported/skipped method counts,
unsupported categories, generation time, incremental generation time). JSON+Markdown reports are written
(experiments/fault-detection-*.{md,json}); division-by-zero is guarded. No production code changed in
M0, so M0 is documentation + evidence only.

Latest (2026-11-03, branch feature/complete-live-demo): **M1 — method identity and overload
resolution (MASTER prompt M1)** — VERIFIED. Member identity is now type-aware: `MemberId` carries the
declared parameter type names (simple form) in addition to `className + memberName + arity + kind`, so
two overloads that share a name AND an arity but differ in parameter type (`f(int)` vs `f(String)`) are
distinct. When the declared types are unknown the identity degrades exactly to the previous behaviour
(empty type list) and can only over-select. Files changed (production): `graph/MemberId.java`
(type-aware identity, `simpleTypeName`, `possiblyCompatible`, `UNKNOWN_TYPE`), `parser/MethodInfo.java`
(carries `parameterTypes`), `parser/JavaParserAnalyzer.java` (extracts declared parameter types),
`impact/MemberChangeDetector.java` and `impact/ChangeImpactAnalyzer.java` (build type-aware MemberIds),
`graph/DependencyGraphBuilder.java` (infers literal/`new X(...)`/cast argument types at every call
site), `graph/DependencyGraph.java` (accumulates per-edge call-site arities AND argument-type lists;
adds `getCallersByMethodNameAndTypes`), `impact/ImpactAnalyzer.java` (narrows the FIRST hop by declared
parameter types when known). Conservative by construction: an unknown argument type, an edge with no
usable type evidence, or a caller that invokes several overloads all KEEP the caller, so type narrowing
can only remove a caller that provably passes incompatible arguments to a same-arity overload. Also
fixed a latent recall bug: call-site arity/type metadata was previously overwritten by the last call
site on an edge; it is now accumulated, so no earlier call site is lost. Tests added: `MemberIdTest`
(+5: same-arity type separation, degradation, `possiblyCompatible`, `simpleTypeName`),
`DependencyGraphOverloadTypeTest` (new, 5) and `ImpactAnalyzerOverloadTypeTest` (new, 3). Verification:
focused suite 19 tests (MemberIdTest 8, DependencyGraphOverloadTypeTest 5, ImpactAnalyzerOverloadTypeTest
3, DependencyGraphArityTest 3) BUILD SUCCESS; `mvn -B clean test` 420 run, 0 failures, 0 errors, 0
skipped, BUILD SUCCESS, 7:30 min; `mvn -B clean package` 420 run, 0 failures, BUILD SUCCESS (shaded JAR
rebuilt). Evidence: docs/verification/2026-11-03-m1-mvn-test.txt,
docs/verification/2026-11-03-m1-mvn-package.txt. Known limitations still open for later milestones:
cross-class callee resolution (callee class still `UNKNOWN`), inheritance/overrides/interfaces, static
imports, method references and renamed-method detection (M2/Area A); the explicit
RESOLVED/AMBIGUOUS/UNKNOWN/UNSUPPORTED resolution-state vocabulary is not yet modelled (target: M3).

Previous (2026-11-02, branch feature/complete-live-demo, extends T3 commit ac8ea52): PHASE 1-14 of the
MASTER prompt (interactive CLI + remote Git repository support + multi-language classification),
added by extending the existing implementation (nothing was replaced or duplicated). New
`cli/InteractiveCli` is a menu session (`--interactive`, or zero-argument from a real console):
it prompts `Enter Git repository URL (or 'exit' to quit):`, validates and clones the URL into a
temporary workspace, prints the repository/branch/latest/previous commit info, offers
`Select comparison:` (latest two commits, or custom base/target) and an operations menu of six items
(analyze / analyze+select / generate / analyze+generate+execute / full workflow / exit), returning to
the menu after every operation and never crashing on empty input, an invalid URL, an invalid selection
or end-of-input. New `cli/RemoteWorkflowCli` is the non-interactive remote form (`--url=<GIT_URL>
[--base= --target=] [--generate] [--execute] [--yes]`): it defaults to the latest two commits of the
default branch, requires `--yes` before running Maven from a cloned repository, and supports the full
workflow. New `remote/RemoteRepository` validates a URL (`https`/`http`/`ssh`/`git`/scp-like/`file`),
clones with a bounded timeout into a `rts-remote-*` temp directory, sanitises the project name against
path traversal, redacts credentials, reports the default branch and the latest/previous commit, and
deletes the workspace on close. New `workflow/AnalysisWorkflow` is the single orchestrator shared by
every entry point and produces the PHASE-11 consolidated terminal report (REPOSITORY INFORMATION,
PROJECT DISCOVERY, FILE CLASSIFICATION, CHANGE DETECTION, DEPENDENCY AND IMPACT ANALYSIS, REGRESSION
TEST SELECTION with the `((total - selected) / total) * 100` reduction, AUTOMATIC TEST GENERATION,
TEST EXECUTION, EXECUTION STATUS SUCCESS/PARTIAL SUCCESS/FAILED/NOT RUN, FINAL RESULT). PHASE 3
multi-language classification was added additively to `impact/ChangedFileClassifier` (`FileCategory`
A-H plus `categorize`, `isJavaChange`, `countByCategory`, `displayName`); the existing `Category`
enum/`classify`/`isUnanalysableChange` behaviour is unchanged, so `ChangedFileClassifierTest` and the
H4 FALLBACK behaviour are preserved. A change set with no Java change but a changed build/resource file
still falls through to the real pipeline (conservative FALLBACK, all tests selected); a change set with
only other-language/documentation/asset files prints the exact PHASE-10 sentence and reports NOT RUN
without fabricating success. Exit codes: the local `SelectTestsCli` contract is unchanged (0/1/2); the
remote and interactive entry points extend it with `3` (test execution failure) and `4` (repository
cloning/network failure). Verification: focused suite 39 tests (InteractiveCliTest 8,
RemoteWorkflowCliTest 10, RemoteRepositoryTest 9, ChangedFileClassifierCategoryTest 12) BUILD SUCCESS;
`mvn -B clean test` 403 run, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS; `mvn -B package` 403 run,
0 failures, BUILD SUCCESS (shaded JAR rebuilt). Live: remote `file://` workflow (1 changed file, 1
changed method `Multiplier#multiply`, 5 existing tests, 2 selected, 60.0% reduction, HIGH, NOT RUN as
`--execute` was not requested) exit 0; scripted interactive session exit 0; public
`https://github.com/Chandan-1818/code-change-check.git` cloned and analysed (9 changed files, 82
selected) exit 0; local T3 `--repo= --base= --target= --execute` still exit 0 (5/5 full, 2/2 selected,
60.0% reduction). The sample repository was verified unchanged throughout (`git rev-parse HEAD`
= 7059f4c, `git status --porcelain` empty) and the tool deletes its temporary workspace on close. Two
stale `%TEMP%\rts-remote-*` directories remain from earlier test runs that had to be killed before
their `finally` block ran; they are not produced by the current code. Evidence:
docs/verification/2026-11-02-remote-interactive-verification-summary.txt,
docs/verification/2026-11-02-remote-interactive-focused-tests.txt,
docs/verification/2026-11-02-remote-interactive-mvn-test-full.txt,
docs/verification/2026-11-02-remote-interactive-mvn-package.txt,
docs/verification/2026-11-02-remote-interactive-live-interactive.txt,
docs/verification/2026-11-02-remote-interactive-live-url.txt,
docs/verification/2026-11-02-remote-interactive-live-public-url.txt,
docs/verification/2026-11-02-remote-interactive-live-local-t3.txt and
docs/verification/2026-11-02-remote-interactive-live-help-version.txt.

Previous (2026-11-02, branch feature/complete-live-demo): T3 (end-to-end live Git-diff and regression
demo). `SelectTestsCli` now accepts the named arguments `--repo=<path> --base=<rev> --target=<rev>`
(the positional form is unchanged) and prints a visible Git diff (`Git diff       : N file(s) changed
between A and B (x added, y modified, z deleted)` followed by one `M`/`A`/`D`-marked path per changed
file), the changed members (`Class#member`) and impacted methods (`Class#method`) it derived, the
selected tests with their explanation chains, and a final report block (Repository, Revisions,
Git diff, Changed members, Impacted methods, Selected tests, Untested methods, Confidence, Execution).
Exit codes are 0 = success, 1 = invalid arguments (including an unresolvable revision) and 2 =
analysis failed (including a preflight refusal). Generated tests are no longer analysis-only: with
`--generate --out=<dir>` the generated sources are fed into the same pipeline, and when such a test is
selected for execution its file is copied into the isolated target-revision checkout
(`src/test/java/rts/generated`) before Maven runs, so it is compiled and executed there. The selection
and the final report clearly distinguish the two arms (`[generated]` marker and
`Selected tests   : N (E existing, G generated)`), and a generated test is reported `EXECUTED` /
counted as confirmed only when Surefire reports a matching `class#method`, otherwise
`NOT confirmed by Maven ... (not reported as executed)`. Execution never touches the caller's
repository: it happens inside the temporary JGit clone, and the original sample repository was
verified unchanged (`git status --porcelain` empty, HEAD still 7059f4c). Full suite 364 run,
0 failures, 0 errors, 0 skipped, BUILD SUCCESS; focused CLI/execution suite 50 tests, BUILD SUCCESS;
`mvn -B package` BUILD SUCCESS (364 tests, shaded JAR rebuilt). Live demo on the sample repository
V4 (a3d0163) -> V5 (268a165): 1 file changed (`M src/main/java/com/sample/Multiplier.java`), 1 changed
member (`Multiplier#multiply`), 2 impacted methods, 2 selected tests; `--execute` passed 5/5 full and
2/2 selected (60.0% test reduction); the generated variant selected 26 tests (2 existing + 24
generated) and confirmed 24/24 generated executed by Maven, exit code 0. Evidence:
docs/verification/2026-11-02-t3-live-report.txt (named-arg static run),
docs/verification/2026-11-02-t3-live-execute.txt (`--execute`),
docs/verification/2026-11-02-t3-live-generated-execute.txt (`--generate --execute`),
docs/verification/2026-11-02-t3-focused-tests.txt (50 focused tests),
docs/verification/2026-11-02-t3-mvn-test-full.txt (364 tests) and
docs/verification/2026-11-02-t3-mvn-package.txt (package).

Previous (2026-11-02, branch feature/complete-live-demo): T2 (isolated test-execution verification
and hardening). Verified that `--execute` checks out and executes tests against the exact analysed
target revision (V5 = 268a165) inside an isolated temporary Git clone, never touching the caller's
working tree or repository. Safety, cleanup, failure handling and timeout behaviour were exercised
end to end: 0 leaked `rts-checkout-*` temp directories before/after, sample repository left clean,
and a forced timeout (`--timeout=1`) reported `Timed out: yes` with `build FAILED` and preserved the
Maven log. Only one verified gap was fixed: `IsolatedRevisionCheckout` used the JGit 6.10-deprecated
`CheckoutCommand.setForce(true)` (which only forces ref updates) and now uses `setForced(true)`,
matching `git checkout --force`. No existing functionality was duplicated. Full suite 356 run,
0 failures, 0 errors, 0 skipped, BUILD SUCCESS; `mvn -B package` BUILD SUCCESS (shaded JAR).
Evidence: docs/verification/2026-11-02-t2-mvn-test-full.txt (356 tests),
docs/verification/2026-11-02-t2-focused-tests.txt (22 focused execution tests),
docs/verification/2026-11-02-t2-mvn-package.txt (package), docs/verification/2026-11-02-t2-live-execute.txt
(live --execute) and docs/verification/2026-11-02-t2-live-failure.txt (timeout/failure path).
Still open: three experiment classes (`GeneratedSuiteRunner`, `FaultInjectionRunner`,
`FaultDetectionExperiment`) still call the deprecated `setForce(true)`; this is outside T2's
"isolated execution only" scope and was left unchanged.

Previous (2026-11-02, branch feature/complete-live-demo): T1 (runnable live demo entry point,
packaging, and documentation). Full suite 356 run, 0 failures, 0 errors, 0 skipped,
BUILD SUCCESS. Executable shaded JAR built via maven-shade-plugin (target/CODE-CHANGE-ANALYSIS-1.0-SNAPSHOT.jar)
with main entry point DemoCli and exec-maven-plugin configured. Verified on sample repository
V4 (a3d0163) -> V5 (268a165): static demo selected 2 tests (testCompute transitive, testMultiply direct,
confidence HIGH); --execute verified in isolated checkout (5/5 full passed, 2/2 selected passed,
60.0% test reduction, 0 failures, 0 timeouts). Root README.md created and DemoCliTest added (8 tests).
Evidence: full suite 356 tests passed, mvn package successful, DemoCli live execution verified.

Previous (2026-11-01, branch safety-fixes-v3, HEAD 42f0966): G8 fault-detection gain measured;
Phase 8B G8 is PARTIAL (fault-detection gain DONE; coverage before/after and supported-vs-skipped
still NOT STARTED). Full suite 348 run, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS.
Evidence: docs/verification/2026-11-01-mvn-test-g8.txt (348 tests),
docs/verification/2026-11-01-g8-focused-tests.txt (14 focused tests) and
docs/verification/2026-11-01-g8-experiment.txt (17-fault two-arm experiment: manual-only mutation
score 15/17=0.8824, manual+generated 16/17=0.9412, additional detection = 1 (`Calculator.negate`),
regressions = 0, undetected in both = 1 (`CheckoutService.quote`)). Results:
experiments/fault-detection-report.md, experiments/fault-detection-results.json.

Previous (2026-11-01, branch safety-fixes-v3, HEAD 42f0966): H10 ground-truth precision/recall
evaluation added; GAP-17 closed. Full suite 334 run, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS.
Evidence: docs/verification/2026-11-01-mvn-test-h10.txt (334 tests) and
docs/verification/2026-11-01-h10-focused-tests.txt (40 focused tests) and
docs/verification/2026-11-01-h10-ground-truth-experiment.txt (17-case experiment, TP=37, FP=18,
FN=0, precision=0.6727, recall=1.0000). Results: experiments/ground-truth-report.md,
experiments/ground-truth-results.json.

Earlier (2026-11-01, branch safety-fixes-v3, HEAD 42f0966): H9 preflight validation added; full
suite 294 run, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS. Evidence:
docs/verification/2026-11-01-mvn-test-h9.txt (294 tests) and
docs/verification/2026-11-01-h9-focused-tests.txt (25 focused tests).

Date: 2026-10-30. Commit tested: 77fe866 (branch safety-fixes-v3).
Evidence: docs/verification/2026-10-30-mvn-test-full.txt
(mvn -B test, all tests: 164 run, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS, JDK 23.0.2,
compiled to target 17). Earlier runs: docs/verification/2026-10-26-mvn-test-*.txt (commit b0aa554).

H5 (selection confidence, typed parse errors, Java 17 language level) added 2026-11-01 on the
working tree (not yet committed). Evidence: docs/verification/2026-11-01-mvn-test-h5.txt
(180 run, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS).

H6 (configurable impact depth with truncation reporting) added 2026-11-01 on the working tree
(not yet committed). Evidence: docs/verification/2026-11-01-mvn-test-h6.txt
(192 run, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS). D-04 confirmed: unlimited depth is the
default; a configured finite limit reports truncation; 0 and negatives other than -1 are rejected.
Focused review accepted 2026-11-01: `hasUnexploredCaller` verified correct for branching, cycles,
already-visited nodes and finite limits; CLI/report truncation observable; public constructors and
overloads backward compatible; no defect found. GAP-07 closed; D-04 confirmed.

Status words follow the vocabulary in the handover documentation (CLAUDE.md is not present in this
repository and must not be assumed). A component is VERIFIED only where the recorded run
executes it and passes. Passing tests do not close a gap in the gap register.

H3 (Stage A identity: member ids, arity-aware selection, constructor participation) added
2026-11-01 on the working tree (not yet committed). Evidence:
docs/verification/2026-11-01-mvn-test-h3.txt (207 run, 0 failures, 0 errors, 0 skipped,
BUILD SUCCESS). Scope: `MemberId(className, memberName, arity, kind)` alongside the unchanged
`MethodNode`; `MemberChangeDetector`/`MemberChangeResult` (member-level, arity-aware); constructor
declarations become graph nodes and `new X(...)`/`this(...)` become edges with a resolved class;
the changed member's arity narrows only the FIRST reverse-BFS hop. Conservative fallbacks: callers
whose call-site arity is unknown are still included, varargs members have arity disabled, and a
non-member change (field/constant/header/import) still expands the whole file. Fix during the run:
constructor callers are matched by class, not by the shared "<init>" name, so a change to one class's
one-argument constructor no longer selects tests that construct a different class (`LargerSampleHistoryTest`
V11->V12 regression). Not resolved by H3: same-arity/different-type overloads still collapse (no type
resolution), and callee class names remain UNKNOWN (GAP-06 stays OPEN). GAP-20 and GAP-12 were already closed by H5; GAP-07 by H6.

H4 (class-level and non-method change triggers) added 2026-11-01 on the working tree (not yet
committed). Evidence: docs/verification/2026-11-01-mvn-test-h4.txt (225 run, 0 failures, 0 errors,
0 skipped, BUILD SUCCESS). Scope: `NonMethodChangeDetector` now also fingerprints record declarations
(header/components) and annotation types (whole declaration, since their elements are not methods);
new `ChangedFileClassifier` classifies each changed path (Java source/test, build, resource, other);
`ChangeImpactAnalyzer` gained a changed-files overload. A changed build file (e.g. pom.xml), resource,
or any other non-Java file cannot be analysed at member level, so the run returns FALLBACK confidence
and selects every discovered test (`TestSelectionReport.getBuildOrResourceChanges()`). A changed Java
file that no detector explained (e.g. an added import) is now an uncertainty trigger that expands the
whole file (REDUCED) instead of being silently ignored. Wired into `ExperimentRunner` and
`SelectTestsCli`. Comment-only changes still select nothing.

H2 (configurable sample path with a clear diagnostic) added 2026-11-01 on the working tree (not yet
committed). Evidence: docs/verification/2026-11-01-mvn-test-h2.txt (229 run, 0 failures, 0 errors,
0 skipped, BUILD SUCCESS). Scope: the sample-project directory is resolved in one place
(`testsupport/SamplePaths`) in the order system property `rts.sample` (preferred), environment variable
`RTS_SAMPLE_PROJECT`, then the original default path, so behaviour is unchanged when nothing is set.
`SamplePaths.sampleProject()` now validates the directory (Path-based, platform-portable) and fails with
an actionable message instead of an obscure git/parse error. The two negative CLI tests no longer
hard-code a Windows `D:/...` path. This is test support only and stays separate from the target-repository
path a user supplies to the CLIs at run time.

H7 (test discovery) added 2026-11-01 on the working tree (not yet committed). Evidence:
docs/verification/2026-11-01-mvn-test-h7.txt (249 run, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS).
Scope: new `impact/TestSourceProvider` discovers the tests of a test source, recognising every JUnit 5
test annotation (`@Test`, `@ParameterizedTest`, `@RepeatedTest`, `@TestFactory`, `@TestTemplate`);
lifecycle methods (`@BeforeEach`, `@BeforeAll`, `@AfterEach`, `@AfterAll`) are attributed to their own
class's tests and to those of nested classes, so a change reachable only through a lifecycle method still
selects the class's tests; a `@Nested` test keeps the enclosing-type identifier (`Outer$Inner#method`) and
is no longer also attributed to the enclosing class. `ChangeImpactAnalyzer` now discovers **every**
`src/test/java/**.java` file at the new revision (not only the `Foo`->`FooTest` pairs), so a
non-conventionally named test file is associated with production code through the call graph (GAP-08).
Added or modified test methods are selected unconditionally; tests that call a method removed between the
revisions are reported as potentially broken without changing the impact-based selection; and every
selected test carries an explanation chain via the new `parents` map in `ImpactSet`
(`ImpactSet.explanationChain`, `TestSelectionReport.getExplanations`). `SelectTestsCli` prints new
"Selection reasons" and "Potentially broken tests" sections. The Phase 8B split is preserved:
`TestSourceProvider` discovers manual test sources only; generated/`extraTestSources` are supplied by the
caller and are never treated as "modified in the repository" (MR-9 remains Phase 8B).
Still not resolved: enum/record test containers are not inspected (GAP-13 partial); callee class names
remain UNKNOWN, so an explanation chain's final node is rendered from the changed member's identity
(GAP-06 stays OPEN).

H8 (execution-pipeline hardening) added 2026-11-01 on the working tree (not yet committed). Evidence:
docs/verification/2026-11-01-mvn-test-h8.txt (269 run, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS)
and docs/verification/2026-11-01-h8-focused-tests.txt (24 focused tests, BUILD SUCCESS).
Scope: `--execute` now runs Maven in an isolated temporary checkout of the requested new revision
(`execution/IsolatedRevisionCheckout`, GAP-14), so the user's working tree is never modified and a stale
working directory can no longer decide the result; the clone is deleted on close, and on failure the
Maven log and Surefire reports are copied to a preserved directory whose path is printed. `TestRunner`
is bounded by a configurable timeout (`--timeout=<ms>`, default 300000 ms; GAP-15): a run that exceeds
it has its whole process tree terminated and is reported as `timedOut`, distinctly from a test failure,
with the partial output kept in `target/maven-execution.log`. Per-test results come from a new
`execution/SurefireReportParser` that reads the Surefire XML (GAP-16), so the summary names passed,
failed (assertion), errored (execution) and skipped tests and distinguishes a failure from an error; a
missing, malformed or partial report is skipped without crashing, and when no XML exists no per-test
detail is claimed (the console summary is used instead). A build that fails to compile is reported as
`compilationFailed` with zero tests, never as a run in which every test failed. `SelectTestsCli` prints
a summary of target revision, selected count, passed/failed/errored/skipped/timed-out counts,
compilation status and duration. The pinned `SelectTestsCliExecuteTest` was migrated (not removed) to
the new summary labels and to V5's real suite, with the GAP-14 justification documented in its Javadoc.
Still open: the general `mvn`-only assumption of `TestRunner`. GAP-17 is now closed by H10.

H9 (preflight validation) added 2026-11-01 on the working tree (not yet committed). Evidence:
docs/verification/2026-11-01-mvn-test-h9.txt (294 run, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS)
and docs/verification/2026-11-01-h9-focused-tests.txt (25 focused tests, BUILD SUCCESS).
Scope: new `preflight/Preflight` inspects the target repository on the filesystem **before** any
analysis, test selection or execution, and returns a structured `preflight/PreflightResult` whose
status is `SUPPORTED`, `REDUCED` or `UNSUPPORTED`, derived from the reason lists (so a caller cannot
report SUPPORTED while holding a rejection). It never returns console strings only. Rejections
(UNSUPPORTED): the repository path is missing/not a directory; no `.git` **directory**; a `.git`
that is a **file** (a linked worktree or submodule gitdir pointer — this is GAP-19, refused rather
than silently mis-read); a multi-module Maven project (`<modules>` in `pom.xml`); a JUnit 4 project
(`junit:junit` or `junit-vintage` dependency, or `org.junit.*` annotations/`Assert` in sources).
Reductions (REDUCED, analysis still runs): Spring (`org.springframework`), Mockito
(`org.mockito`), Lombok and reflection (`java.lang.reflect`, `Class.forName`, `getDeclared*`) usage
that can hide call edges, so selection reliability is lower. `SelectTestsCli` runs preflight before
its analysis `try` block: an UNSUPPORTED repository is refused with exit code 2 and the message
`Analysis failed: preflight validation refused this repository.` followed by one actionable line per
reason; a REDUCED repository prints a `Preflight` block (after the `Confidence` line) explaining
which detected usage lowers confidence. A missing `pom.xml` is **not** a rejection, so plain/temp
Git repositories without a build file remain SUPPORTED. Existing public signatures and all H2-H8
behaviour are unchanged. Open: the plain/temp-repo tests still pass and the general `mvn`-only
assumption remains. GAP-17 is now closed by H10.

H10 (ground-truth precision/recall evaluation) added 2026-11-01 on the working tree (not yet
committed). Evidence: docs/verification/2026-11-01-mvn-test-h10.txt (334 tests, BUILD SUCCESS),
docs/verification/2026-11-01-h10-focused-tests.txt (40 focused tests, BUILD SUCCESS), and
docs/verification/2026-11-01-h10-ground-truth-experiment.txt (17-case experiment executed).
Scope: new `eval` package — `GroundTruthCase`, `GroundTruthDataset`, `GroundTruthEvaluator`,
`PrecisionRecall`, `CaseResult`, `EvaluationReport` — provides a complete, standalone precision/recall
evaluation framework separate from normal production selection. `PrecisionRecall.of(selected, required)`
computes TP, FP, FN from set intersection; zero denominators return `null` (rendered `"n/a"`) so
an empty selection is never silently promoted to 100% precision. `GroundTruthDataset` is a
pipe-delimited text format with per-line error reporting; `GroundTruthEvaluator` drives the evaluation
through a `TestSelector` abstraction (fake implementations keep unit tests repository-free).
`EvaluationReport` produces deterministic Markdown and JSON output. The production adapter
`AnalysisTestSelector` implements `TestSelector` using the real `ChangeImpactAnalyzer`. The
reproducible experiment command is `FaultInjectionEvaluator.main()`:
`FaultInjectionEvaluator <repoPath> <baseRevision> [grown] [outputDir]`. It calls
`FaultInjectionRunner` (which injects faults and runs the full suite to establish ground truth),
maps each result to the `eval` model, prints the Markdown report, and writes
`experiments/ground-truth-report.md` and `experiments/ground-truth-results.json`.
Experiment result (17 faults at V16=7059f4c, grown sample): TP=37, FP=18, FN=0;
precision=0.6727 (lower bound, D-16), recall=1.0000 (exact — no required test was missed).
Two cases (`Calculator.negate`, `CheckoutService.quote`) had no failing tests and were not selected,
so both precision and recall are `n/a`. Dataset limitation: single-line faults only; cases where a
fault kills no test contribute `n/a` metrics but are still valid (the tool correctly selected
nothing). GAP-17 CLOSED.

| Component | Status | Evidence | Open gaps |
|---|---|---|---|
| Git access | VERIFIED | GitAnalyzerTest, GitAnalyzerListFilesTest in the full run | none (GAP-19 closed by H9) |
| Edge cases (untested, removed, deleted, new file) | VERIFIED | CLI and ChangeImpactAnalyzer tests in the full run, H7TestDiscoveryIntegrationTest (removed-method callers) | none (GAP-11 closed by H7) |
| Reduction metrics | VERIFIED | EvaluationMetricsIntegrationTest | time reduction unreliable on small suites |
| Sample project V1-V16 | VERIFIED | all 16 hashes resolve; tests pass at pinned revisions | repo is outside this repository |
| Method change detection | IN PROGRESS | MethodChangeDetectorSafetyTest, NonMethodChangeDetectorTest pass | name-only identity (GAP-01, 06) |
| Member identity + arity-aware selection + constructors (H3) | VERIFIED | MemberIdTest, DependencyGraphArityTest, MemberChangeDetectorTest, MemberAwareSelectionIntegrationTest in docs/verification/2026-11-01-mvn-test-h3.txt | same-arity overloads collapse (GAP-01 partial); callee UNKNOWN (GAP-06); inheritance/override edges (GAP-05 partial) |
| Class-level + build/resource triggers (H4) | VERIFIED | ChangedFileClassifierTest, NonMethodChangeDetectorTest, ChangeImpactAnalyzerBuildResourceFallbackTest in docs/verification/2026-11-01-mvn-test-h4.txt | build/resource changes only escalated, not analysed (GAP-04 partial); class-header triggers additive-only (GAP-03 partial) |
| Dependency graph, impact, selection | IN PROGRESS | tests pass | GAP-05, GAP-13 (partial) |
| Test discovery (H7) | VERIFIED | TestSourceProviderTest, H7TestDiscoveryIntegrationTest, ImpactSetExplanationChainTest in docs/verification/2026-11-01-mvn-test-h7.txt | enum/record test containers not inspected (GAP-13 partial); callee UNKNOWN in explanation chain (GAP-06) |
| Impact depth configurable + truncation reporting (H6) | VERIFIED | ImpactAnalyzerDepthTest, SelectTestsCliTruncationTest in docs/verification/2026-11-01-mvn-test-h6.txt | GAP-07 closed by H6 |
| CLI tools, test execution | IN PROGRESS | tests pass | enum/record discovery (GAP-13 partial) |
| Execution hardening: isolated new-revision checkout, timeout, per-test results, compile-failure detection (H8) | VERIFIED | SurefireReportParserTest, TestRunnerParseResultTest, IsolatedRevisionCheckoutTest, TestRunnerExecutionHardeningTest, SelectTestsCliExecuteTest, TestRunnerIntegrationTest in docs/verification/2026-11-01-h8-focused-tests.txt | GAP-14, 15, 16 closed; timeout/compile/per-test proven on the sample project |
| Fault injection (9 at V8, 17 at V16) | VERIFIED for zero MISSED on these faults | FaultInjectionRunnerTest, FaultInjectionGrownSampleTest | single-line faults only |
| Ground-truth precision/recall evaluation (H10) | VERIFIED | PrecisionRecallTest, GroundTruthCaseTest, GroundTruthDatasetTest, EvaluationReportTest, GroundTruthEvaluatorTest, GroundTruthEvaluationIntegrationTest in docs/verification/2026-11-01-h10-focused-tests.txt; experiment in docs/verification/2026-11-01-h10-ground-truth-experiment.txt | GAP-17 closed; single-line faults only; 2 n/a cases (no failing tests) |
| Sample path configurable with clear diagnostic (H2) | VERIFIED | SamplePathsTest in docs/verification/2026-11-01-mvn-test-h2.txt | GAP-18 closed |
| Phase 8B test generation | IN PROGRESS | see below | G8 (fault-detection gain DONE), TestSourceProvider |
| Selection confidence, typed parse errors, Java 17 level (H5) | VERIFIED | SelectionConfidenceTest, JavaParserAnalyzerTest, SelectTestsCliTest in docs/verification/2026-11-01-mvn-test-h5.txt | GAP-12 closed by H5 |
| Preflight validation (H9) | VERIFIED | PreflightTest, PreflightResultTest, PreflightCliIntegrationTest in docs/verification/2026-11-01-h9-focused-tests.txt | GAP-19 closed by H9; multi-module/JUnit4/`.git` refusals and Spring/Mockito/Lombok/reflection REDUCED verified |
| Live demo entry point (T1) | VERIFIED | DemoCliTest (8 tests in docs/verification/2026-11-02-t1-tests.txt), mvn package (executable shaded jar), java -jar, and live demo --execute on sample repo V4->V5 | none |
| Isolated test execution verification & hardening (T2) | VERIFIED | 22 focused execution tests (IsolatedRevisionCheckoutTest, TestRunnerExecutionHardeningTest, SurefireReportParserTest, TestRunnerParseResultTest, SelectTestsCliExecuteTest) in docs/verification/2026-11-02-t2-focused-tests.txt; full suite + package in 2026-11-02-t2-mvn-test-full.txt / 2026-11-02-t2-mvn-package.txt; live runs in 2026-11-02-t2-live-execute.txt and 2026-11-02-t2-live-failure.txt | GAP-14, 15, 16 closed; deprecated `setForce` remains in 3 experiment classes (out of T2 scope) |
| End-to-end live Git-diff and regression demo (T3) | VERIFIED | SelectTestsCliNamedArgsTest, SelectTestsCliGeneratedExecuteTest, DemoCliTest in docs/verification/2026-11-02-t3-focused-tests.txt; live runs in 2026-11-02-t3-live-report.txt / 2026-11-02-t3-live-execute.txt / 2026-11-02-t3-live-generated-execute.txt | generated tests are characterization tests (pinned oracles); callee class names remain UNKNOWN (GAP-06) |
| Remote Git URL support (PHASE 2/12) | VERIFIED | RemoteRepositoryTest (9 tests), RemoteWorkflowCliTest (10 tests) in docs/verification/2026-11-02-remote-interactive-focused-tests.txt; live public clone in 2026-11-02-remote-interactive-live-public-url.txt and file:// in 2026-11-02-remote-interactive-live-url.txt | exit code 4 on clone/network failure; private repos need an external credential provider |
| Interactive CLI session (PHASE 1) | VERIFIED | InteractiveCliTest (8 tests) in docs/verification/2026-11-02-remote-interactive-focused-tests.txt; scripted live session in 2026-11-02-remote-interactive-live-interactive.txt | zero-arg opens the menu only from a real console (DemoCliTest keeps the static demo) |
| Multi-language classification A-H (PHASE 3) | VERIFIED | ChangedFileClassifierCategoryTest (12 tests) plus retained ChangedFileClassifierTest in docs/verification/2026-11-02-remote-interactive-focused-tests.txt | non-Java files are classified and reported, never parsed as Java |
| Shared orchestrator + consolidated report (PHASE 11) | VERIFIED | RemoteWorkflowCliTest asserts every PHASE-11 section header; live reports in 2026-11-02-remote-interactive-live-url.txt / 2026-11-02-remote-interactive-live-interactive.txt | analysis-only status is NOT RUN; SUCCESS requires confirmed execution |
| Backend, database, dashboard, report | NOT STARTED | none | GAP-26 |

## Phase 8B tasks

| Task | Status | Evidence |
|---|---|---|
| G1 spec, G2 discovery, G3 inputs and receivers, G4 oracle capture | VERIFIED | unit and integration tests in the full run |
| G5 emitter, manifest, writer | VERIFIED | TestEmitterTest, GeneratedTestWriterTest, TestGeneratorTest |
| G6 incremental update (hash, pinned oracle, accept, retire) | VERIFIED | IncrementalGeneratorTest and 4 other classes |
| G7 execution of generated suite | VERIFIED on the sample | GeneratedSuiteRunnerIntegrationTest: 98 generated tests, 5 methods skipped, 115 of 115 passed at V16 |
| G7 CLI (GenerateTestsCli) | VERIFIED | GenerateTestsCliTest, GenerateTestsCliRealHistoryTest |
| TestSourceProvider (generated tests in selection) | NOT STARTED | H7 implements the manual-test provider (`impact/TestSourceProvider` discovers and associates `src/test/java/**.java`); the Phase 8B component that feeds generated tests through the same provider is still absent (the `--generated` CLI path already adds generated sources to the selection) |
| G8 metrics (coverage before/after, validity, fault-detection gain) | PARTIAL — fault-detection gain DONE | `FaultDetectionExperimentTest` (14 tests) in docs/verification/2026-11-01-g8-focused-tests.txt; two-arm experiment in docs/verification/2026-11-01-g8-experiment.txt; results in experiments/fault-detection-report.md and experiments/fault-detection-results.json | coverage before/after and supported-vs-skipped still NOT STARTED |

Real-history result (GenerateTestsCliRealHistoryTest): initial suite at V13 (98 tests);
V13 to V14 reports TaxCalculator.calculateTax(int) as modified and BEHAVIOUR_CHANGED; V14 to V15
adds CheckoutService.quote(String,int) (110 tests); V15 to V16 retires Calculator.compute(int,int)
(98 tests). Running the V14 suite at V14: 116 tests, 113 passed, 3 failed, all three failing tests
being gen_TaxCalculator_calculateTax_02, _04, _05 (the intended behaviour-change signal).

Caveats: the sample already had tests, so the no-existing-tests case is covered only up to
compilation (TestGeneratorTest). Pinned oracles do not detect a change in a callee. Dependabot
reports 1 moderate alert; package not yet identified.

JDK 17 execution verification is OUTSTANDING. Every recorded baseline so far was produced on
JDK 23.0.2 compiling to target 17, so the required "JDK 17" runtime has never actually been
executed. H5 configures the JavaParser language level to Java 17 (records, text blocks, switch
expressions), but that is a parse-time setting, not proof that the suite runs on a JDK 17 VM.

G8 metrics are PARTIAL. Fault-detection gain is DONE and measured (`FaultDetectionExperiment`,
2026-11-01): on 17 single-line V16 faults, manual-only mutation score 15/17=0.8824 vs
manual+generated 16/17=0.9412, additional detection = 1 (`Calculator.negate`), regressions = 0,
undetected in both = 1 (`CheckoutService.quote`). Coverage-before/after and supported-vs-skipped
are still NOT STARTED; no coverage-before/after figure alone may be read as G8 completion.

## Slow tests (launch Maven; see SETUP_AND_EXECUTION_GUIDE.md section 4)
TestRunnerIntegrationTest, EvaluationMetricsIntegrationTest, SelectTestsCliExecuteTest,
SelectTestsCliGeneratedExecuteTest, ExperimentExecutorTest, ExperimentCliExecuteTest,
FaultInjectionRunnerTest, FaultInjectionGrownSampleTest, GeneratedSuiteRunnerIntegrationTest,
GenerateTestsCliRealHistoryTest.
