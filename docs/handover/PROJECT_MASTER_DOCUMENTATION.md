# PROJECT_MASTER_DOCUMENTATION.md

**Change-Sensitive Regression Test Selection Using Code Impact Analysis**
MCA final-year project (12 credits), PES University, Department of Computer Applications.
Maven module `com.analysis.tool:CODE-CHANGE-ANALYSIS`, Java 17, JUnit 5.10.2, JGit 6.10.0, JavaParser 3.26.2. Developer environment: Windows 11, Git Bash, local Git repo with GitHub remote.

Companion files: `SOURCE_CODE_INVENTORY.md`, `PROJECT_STATUS_REPORT.md`, `TECHNICAL_ISSUES_AND_DECISIONS.md`, `PROJECT_ROADMAP.md`, `SETUP_AND_EXECUTION_GUIDE.md`, `REQUIREMENTS_TRACEABILITY_MATRIX.md`, `TRAE_AI_HANDOVER_PROMPT.md`. Original authorities in the repo root: `MCA_Project_Synopsis_UPDATED.docx`, `project_blueprint_UPDATED.md`, `PROJECT_ROAD_MAP_UPDATED.md`, `CLAUDE.md`.

## 1. Purpose
Regression suites re-run in full after every commit are costly. The tool compares two Git revisions of a Java/Maven/JUnit 5 project, finds which methods changed, walks a static call graph in reverse to find what is impacted, selects the JUnit tests that reach changed code, optionally runs only those, and compares against the full suite. A **mandatory extension** must generate JUnit tests automatically and **locally** (also for projects with no tests) and keep them in sync with source changes.

The project is an applied prototype, not a new algorithm. Comparable published tools exist (for example Ekstazi, STARTS). The synopsis states the tool does **not** claim complete or guaranteed identification of every impacted test. Never describe selection as "safe" or "sufficient"; report confidence and assumptions.

## 2. Authority and constraints
1. Synopsis sections 1-23: approved. Title, objectives, scope and requirements are never altered without a separately listed, approved proposal (blueprint section 23: PC-01..PC-03).
2. Blueprint: technical design and gap register. 3. Roadmap: the only active plan. 4. `CLAUDE.md`: how to work.
Higher documents win; conflicts go into the blueprint decision log. Do not invent university requirements.

Hard constraints: no external AI API, remote generation or cloud service; generated tests stored outside the tool's `src/test/java` and never committed to a target repository; never delete/disable/weaken tests; never fabricate results; never mark planned work done; no destructive Git operations; Windows 11 and Java 17 compatibility; no new libraries without need.

## 3. Pipeline (blueprint section 4)
```
repoPath, oldRev, newRev
 1 Preflight (planned)           -> compatibility result
 2 Change detection              -> changed methods (+ non-method change expansion)
 3 Parse + dependency graph      -> name-based caller graph
 4 Impact analysis               -> reverse BFS from changed names
 5 Generate/update tests (planned)-> generated tests + manifest, outside tool tests
 6 Test discovery + mapping      -> @Test nodes (manual now; generated planned)
 7 Selection                     -> selected, untested, removed (+ confidence planned)
 8 Execution                     -> full and selected via external Maven
 9 Metrics                       -> reductions, fault-injection safety
10 Report                        -> console / Markdown / CSV (API and dashboard planned)
```
Implementation order differs from the synopsis order on purpose (D-14): mapping is built before the generator.

## 4. What exists (all NOT VERIFIED)
- **Git:** changed files, content at revision, file listing (no rename detection; `.git` directory required).
- **Change detection:** methods by name (overloads grouped, declaration + comment-free body fingerprint); non-method changes (fields, constants, imports, constructors, initializers, class/enum headers) trigger whole-file conservative expansion.
- **Graph:** one node per `(class, method name)`, edges to `UNKNOWN.<callee>`; reverse lookup by method name bridges the unknown class.
- **Impact:** reverse BFS, visited set, depth cap 5 (silent).
- **Selection:** one combined graph over production + paired test sources (`Foo`->`FooTest`, `@Test` only); each changed name is traced separately; untested and removed methods reported; `Class#method` identifiers for Surefire.
- **CLIs:** `SelectTestsCli` (auto-discovery, `--execute`), `ExperimentCli` (`--csv`, `--execute`).
- **Execution:** `mvn test [-Dtest=...]` via `ProcessBuilder`, Surefire summary parsing.
- **Evaluation:** reduction metrics; multi-revision runner; fault-injection runner with 9 faults (V8) and 17 faults (V16) and a MISSED/CAUGHT/NOT_DETECTED_BY_SUITE classification.
- **Sample project (external repo):** 16 pinned revisions V1..V16 under `D:/PROJECT/SAMPLE-PROJECT/sample-project`.

## 5. What does not exist
Automatic test generation (mandatory), preflight and confidence/fallback, depth flag, test discovery beyond naming convention, execution timeout and temp checkout in `--execute`, Spring Boot backend, DB decision, dashboard, recorded experiment results, report, milestone decks, demo. Details and exact gaps: `PROJECT_STATUS_REPORT.md`.

## 6. Why the graph is name-based (and the consequence)
No Symbol Solver is configured, so the declaring class of `x.foo()` is unknown. The project accepted over-selection (never hide a change) over missing chains. Consequences: tests assert `MethodNode("UNKNOWN", ...)`; any change to identity (task H3) requires written justification for migrated tests. Name collisions over-select; omitted edge kinds (constructors, initializers, inheritance, fields) can under-select (U-severity gaps).

## 7. Mandatory test generation (design summary; blueprint section 12)
- Characterization tests: record current behaviour; detect behaviour change; do not prove correctness.
- Supported v1: public methods of concrete non-generic classes; params/returns primitive, boxed, `String`, `void`; receivers via no-arg or supported constructor or recursively constructible project classes.
- Skipped with reason codes: `ABSTRACT`, `UNSUPPORTED_PARAM`, `UNSUPPORTED_RETURN`, `NONDETERMINISTIC_API`, `NO_RECEIVER`, `NONDETERMINISTIC_RESULT`, `TIMEOUT`, `CRASH`, `NOT_TESTABLE`.
- Inputs: seeded boundary pools, case cap 12, seed 42 (configurable). Oracle: run in a **separate JVM** with timeout and output cap, **twice in separate JVMs**, drop differing cases.
- Output: `<Class>GeneratedTest` in package `rts.generated`, methods `gen_<Class>_<method>_<nn>` (unique; graph matches by name); `manifest.json` (schema in blueprint 12.7); storage `generated-tests/<project>/<revision>/`; copied into `src/test/java/rts/generated/` of a temporary checkout only.
- Incremental: added -> generate; modified -> re-run, `BEHAVIOUR_CHANGED`, regenerate only after explicit accept; removed -> retire; unchanged -> keep. Manual tests never touched.
- Initial targets with no test in the sample: `Calculator.negate`, `CheckoutService.quote`.
- Open design point: how to compile the target and obtain its classpath offline (decide in task G1).

## 8. Compatibility profile (blueprint section 14)
Supported: Git repo with a `.git` directory, single-module Maven jar project, Java up to 17 syntax, JUnit 5 via Surefire, statically visible calls. Reduced confidence or unsupported: Gradle, multi-module, JUnit 4, heavy reflection/DI, Lombok-style processors, worktrees, submodules. Never claim the tool works on arbitrary repositories.

## 9. Evaluation plan
Metrics: test reduction, time reduction (informational on small suites), precision (lower bound), recall (fault-induced failing tests selected), selection effectiveness, plus generator metrics (method coverage before/after, supported vs skipped, validity, fault-detection gain). At least 8 commit pairs, 3 timing runs each, with and without generated tests, plus a compatibility study on unseen repositories. Report negative results honestly.

## 10. Working method (applies to TRAE too)
INSPECT -> IMPLEMENT -> TEST -> VERIFY -> COMMIT -> PUSH, one logical unit per step, evidence recorded in `docs/verification/`, completion report at the end of each task (files changed, commands and real results, tests run, status changes with evidence path, gaps closed or open, assumptions, next step; say plainly what was not run). Status words: NOT STARTED, IN PROGRESS, IMPLEMENTED — NOT VERIFIED, VERIFIED, BLOCKED.

## 11. Known unknowns
Dates and university format; build/test baseline; sample repo contents; Java 17 parser level; Surefire id handling for `@Nested` and parameterized tests; Randoop/EvoSuite compatibility; depth default; where generated tests live relative to Git.
