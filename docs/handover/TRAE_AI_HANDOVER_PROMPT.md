# TRAE_AI_HANDOVER_PROMPT.md

Paste everything below the line into TRAE AI after placing the eight handover files in the repository (suggested: `docs/handover/`).

---

You are continuing an existing Java/Maven project: **Change-Sensitive Regression Test Selection Using Code Impact Analysis** (MCA final-year project, PES University). Module `com.analysis.tool:CODE-CHANGE-ANALYSIS`, Java 17, JUnit 5.10.2, JGit 6.10.0.202406032230-r, JavaParser symbol-solver 3.26.2. Windows 11, Git Bash, local Git repo with a GitHub remote. **Finish this project; do not rebuild or restructure it.**

## Read, in this order
1. `docs/handover/PROJECT_MASTER_DOCUMENTATION.md`
2. `docs/handover/PROJECT_STATUS_REPORT.md`
3. `docs/handover/SOURCE_CODE_INVENTORY.md`
4. `docs/handover/TECHNICAL_ISSUES_AND_DECISIONS.md`
5. `docs/handover/PROJECT_ROADMAP.md`
6. `docs/handover/REQUIREMENTS_TRACEABILITY_MATRIX.md`
7. `docs/handover/SETUP_AND_EXECUTION_GUIDE.md`
8. Repo root: `CLAUDE.md`, `project_blueprint_UPDATED.md`, `PROJECT_ROAD_MAP_UPDATED.md`, `MCA_Project_Synopsis_UPDATED.docx` (sections 1-23 are approved and frozen; blueprint section 12 is the test-generation design; blueprint section 19 is the gap register).
Authority order: synopsis > blueprint > roadmap > `CLAUDE.md`. Ignore `PROJECT_ROAD_MAP.md`, `project_road_map_2.md`, `project_blueprint.md` if they exist (archived).

## Facts you must preserve (they come from a code audit; overturn them only with repository evidence)
- **Automatic test generation (mandatory) has NOT started.** No `testgen` package, class, test or dependency exists.
- The dependency graph is **name-based** (`MethodNode(className, methodName)`, callee class `UNKNOWN`, matching by method name; depth cap 5 hard-coded and silent).
- Several GAP items are **partially resolved** in code (GAP-01, 02, 03, 20) and **NOT VERIFIED**; the blueprint, roadmap and synopsis section 24 are outdated on this.
- **The build and test baseline is unknown.** Nothing is VERIFIED. 89 `@Test` methods in 35 classes exist (static count).
- The handover documents were written by an assistant that could not run any command. Treat their statements as claims to check.

## Mandatory project requirements
Automatic generation of JUnit tests for the target Java source, **including projects with no existing tests**; additional/updated tests when source changes; **fully local** generation and execution; **no external AI API, remote generation API or cloud service** (in code and in `pom.xml`); integration with Git change detection, impact analysis, dependency graph, regression test selection and test execution; generated tests for target projects **kept separate** from the analysis tool's own tests (stored in `generated-tests/<project>/<revision>/`, git-ignored, copied only into a temporary checkout for execution, never committed to a target repository). Never overwrite manual tests; never overwrite an accepted oracle without an explicit accept command. Generation must be deterministic (fixed seed, no timestamps in output) and every skipped method must be reported with a reason code.

## Rules
- Never restart or restructure the project; keep packages `com.analysis.tool.*`; no unnecessary refactoring or dependencies; do not mix refactoring with feature work.
- Read existing code and tests before changing anything; one logical unit per step; add or update tests with every change.
- **Never delete, disable or weaken a test to pass a build.** Migrating a test that asserts the old name-based API requires a written reason.
- **Never invent** results, counts, timings, hashes or pass statements. Never mark planned work completed. Never call the selection "safe" or "sufficient"; report confidence and assumptions. Never claim the tool works on arbitrary repositories.
- Status words: NOT STARTED, IN PROGRESS, IMPLEMENTED — NOT VERIFIED, VERIFIED, BLOCKED. VERIFIED only after the command was run and output saved to `docs/verification/<date>-<what>.txt` with the command, `git rev-parse --short HEAD` and the output tail. No completion percentages.
- Git: stage only the step's files (no `git add .`); `git diff --check`; descriptive commit; `git push origin HEAD` only after the step's tests pass. No force-push, `reset --hard`, `clean -fd`, history rewrite or branch deletion without my explicit permission. Preserve unrelated working-tree changes. No credentials or `.env` files.
- Windows 11 compatibility; Java 17. Do not touch synopsis sections 1-23; list any proposed academic change separately for approval.
- Do not begin a dependent step while the current one has unresolved failures. If a test fails, read the real output first: rule out environment causes (sample repo at `D:/PROJECT/SAMPLE-PROJECT/sample-project`, the 16 pinned hashes, `mvn` on PATH, JDK 17) before suspecting code.

## Your first response (do NOT code yet)
1. Inspect the real repository and compare it with `SOURCE_CODE_INVENTORY.md` (file lists, counts, `pom.xml`, `.gitignore`).
2. Run the baseline from `SETUP_AND_EXECUTION_GUIDE.md` sections 2-4 (git status, branch, log, tool versions, sample repo and all 16 hashes, compile, fast suite, then the seven slow classes one at a time). Do not modify any file while doing this other than saving evidence under `docs/verification/`.
3. Report: (a) your understanding of the project; (b) differences between the handover documents and the actual repository; (c) actual build and test results, with every failure classified as environment or code; (d) which gaps from `PROJECT_STATUS_REPORT.md` section 4 are confirmed, closed or changed; (e) remaining tasks; (f) the exact next task and why.
4. Wait for my confirmation before any architectural change, new dependency (for example Spring Boot, exec/shade plugins) or change to `pom.xml`.

## Task order after the baseline (details, files and acceptance criteria in `PROJECT_ROADMAP.md`)
R0/H1 baseline -> R1 documentation reconciliation -> H5 (confidence, fallback, typed parse errors, Java 17 language level) -> H6 (depth flag) -> H3 (member identity) -> H4 (class/build/resource triggers) -> H2 (configurable sample path) -> H7 (test discovery) -> H8 (execution hardening) -> H9 (preflight) -> H10 (ground truth) -> **Phase 8B test generation G1-G8** -> Phases 9-12 -> 13-16 -> 17-19 -> 20-23. Do not cut 8B or the evaluation.

## Format of every task you perform
Objective; files involved; inspection of the existing code; proposed change; implementation; the tests you ran and their real output; verification; evidence file path; documentation and status updates (only from evidence); then a completion report: files changed, commands run and actual results, tests run and outcome, status changes with evidence path, gaps closed or still open (by GAP id), assumptions, next step, and plainly what was **not** run.

## Open questions to ask me (do not guess)
Submission/review dates and university report format and P1-P3 requirements; depth default (D-04); generation scope default (D-24); whether generated tests live outside Git (D-23); size of the unseen-repository compatibility set; how to compile the target and obtain its classpath offline in the generator (decide in G1); whether to archive the three old planning files with `git mv` into `docs/archive/`.
