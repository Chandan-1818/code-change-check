# Change-Sensitive Regression Test Selection Using Code Impact Analysis

A lightweight, deterministic regression test selection (RTS) engine for Java projects. The system performs fine-grained static code impact analysis between Git commits to identify and execute only the tests affected by code modifications, reducing test cycle time while maintaining regression test safety.

---

## Table of Contents
1. [Problem Statement](#problem-statement)
2. [How the System Works](#how-the-system-works)
3. [Existing Architecture & Components](#existing-architecture--components)
4. [Technology Stack](#technology-stack)
5. [Prerequisites](#prerequisites)
6. [Installation & Setup](#installation--setup)
7. [Maven Build Instructions](#maven-build-instructions)
8. [Live Demo Launch Command](#live-demo-launch-command)
9. [Interactive Session](#interactive-session)
10. [Remote Git Repository URL](#remote-git-repository-url)
11. [Command-Line Arguments & Options](#command-line-arguments--options)
12. [Multi-Language Change Handling](#multi-language-change-handling)
13. [Expected Output](#expected-output)
14. [Running Existing Tests](#running-existing-tests)
15. [Distinction of Core Functionalities](#distinction-of-core-functionalities)
16. [Security & Reliability](#security--reliability)
17. [Current Limitations](#current-limitations)
18. [Project Structure](#project-structure)

---

## Problem Statement

In modern continuous integration (CI/CD) pipelines, regression testing verifies that new code changes do not break existing software features. However:
- Running the full test suite on every commit is computationally expensive and delays feedback to developers.
- Skipping tests ad-hoc risks missing regression defects when changes propagate through multi-layer method call chains.
- Coarse file-level selection over-selects tests whenever unrelated methods in the same file are changed.

This project addresses these challenges by computing fine-grained, member-level change diffs and traversing the static dependency call graph to select only the tests impacted by the change (both directly and transitively).

---

## How the System Works

The engine is repository-independent and can be driven three ways, all sharing the
*same* analysis pipeline (`AnalysisWorkflow`): a **local repository** (positional or
`--repo=`), a **remote Git URL** cloned into a temporary workspace
(`--url=`), or an **interactive session** (`--interactive`).

1. **Repository Resolution (`RemoteRepository`):**  
   For a remote URL, the repository is validated and cloned into a dedicated temporary workspace, the default branch and its latest two commits are detected (a custom base/target pair can be supplied instead), and the clone is deleted on completion. The user's own repository and the remote are never modified or pushed to.

2. **Preflight Repository Validation (`Preflight`):**  
   Validates the target repository prior to analysis. Refuses unsupported setups (non-Git directories, linked worktrees, multi-module Maven projects, JUnit 4 suites) with actionable diagnostics, and flags potential edge-hiding libraries (Spring, Mockito, Lombok, reflection) with `REDUCED` confidence.

3. **Multi-Language File Classification (`ChangedFileClassifier`):**  
   Classifies every changed path into the eight reporting categories (A–H: Java production, Java test, build, configuration, other languages, documentation, static assets, binary/unknown). Only Java production and Java test changes enter the Java testing scope; a non-Java file is **never** parsed as Java.

4. **Git Change Detection (`GitAnalyzer`):**  
   Uses Eclipse JGit to analyze the diff between two Git revisions (`baseRevision` and `targetRevision`), identifying changed, added, or deleted files.

5. **AST Parsing & Member-Level Change Detection (`JavaParserAnalyzer`, `MemberChangeDetector`):**  
   Parses source files into Abstract Syntax Trees using JavaParser. Detects additions, modifications, and removals of methods, constructors, fields, and record/annotation headers, computing member identities with parameter arity.

6. **Static Reverse Call Graph Construction (`DependencyGraphBuilder`):**  
   Constructs a directed call graph mapping methods and constructors to their callers across production and test code.

7. **Transitive Impact Analysis (`ChangeImpactAnalyzer`, `ImpactAnalyzer`):**  
   Performs a reverse breadth-first search (BFS) from the modified members through the call graph to find all reachable test methods. Unreached modified methods are flagged as warnings, and call chains are recorded as selection reasons.

8. **Isolated Test Execution & Metrics (`IsolatedRevisionCheckout`, `TestRunner`):**  
   When execution is requested (`--execute`), tests run inside an isolated temporary Git checkout of the target revision. Real Maven subprocesses execute the full suite and the selected suite, parsing Surefire XML reports to measure exact pass/fail counts, test count reduction (%), and execution time reduction (%). The checkout is created at the **exact analysed target revision** in the system temp directory, the caller's repository and working tree are never modified, and the temporary clone is deleted on completion. A run that exceeds `--timeout` has its whole process tree terminated and is reported distinctly as timed out, with the Maven log preserved for inspection.

9. **Consolidated Report (`AnalysisWorkflow.Result`):**  
   Every run ends with a single terminal report whose sections are computed dynamically: repository information, project discovery, file classification (A–H counts), change detection, dependency and impact analysis, regression test selection (with the test-reduction figure), automatic test generation, test execution and the final execution status (`SUCCESS` / `PARTIAL SUCCESS` / `FAILED` / `NOT RUN`).

---

## Existing Architecture & Components

```
+---------------------------------------------------------------------------------+
|                               CLI Entry Points                                  |
|   DemoCli (Live Demo)  |  SelectTestsCli (RTS Engine)  |  GenerateTestsCli      |
|   InteractiveCli (menu) | RemoteWorkflowCli (--url=)  |  AnalysisWorkflow      |
+---------------------------------------------------------------------------------+
          |                                  |                         |
          v                                  v                         v
+-------------------+              +-------------------+     +--------------------+
| RemoteRepository  |              | GitAnalyzer       |     | TestGenerator      |
| (JGit Clone)      |              | (JGit Diffing)    |     | (Local Generation) |
+-------------------+              +-------------------+     +--------------------+
          |                                  |
          |        +-------------------+     |
          |        | Preflight Guard   |     |
          |        | (Validation GAP-19)|    |
          |        +-------------------+     |
          |                                  |
          +-----------------+----------------+
                            |
                            v
               +--------------------------+
               | ChangedFileClassifier    |
               | (Multi-Language A-H)     |
               +--------------------------+
                            |
                            v
               +--------------------------+
               | JavaParserAnalyzer       |
               | (AST Member Inspection)  |
               +--------------------------+
                            |
                            v
               +--------------------------+
               | DependencyGraphBuilder   |
               | (Reverse Call Graph)     |
               +--------------------------+
                            |
                            v
               +--------------------------+
               | ChangeImpactAnalyzer     |
               | (Transitive BFS Impact)  |
               +--------------------------+
                            |
                            v
               +--------------------------------------+
               | IsolatedRevisionCheckout & TestRunner |
               | (Isolated Maven Execution & Metrics) |
               +--------------------------------------+
```

- **`com.analysis.tool.cli.DemoCli`**: Unified live demo entry point supporting zero-configuration sample execution and custom repository analysis.
- **`com.analysis.tool.cli.SelectTestsCli`**: Core command-line interface for regression test selection and execution.
- **`com.analysis.tool.cli.InteractiveCli`**: Interactive menu-driven session (`--interactive`); prompts for a Git URL, detects revisions, and offers the six operations from analysis to the full regression workflow.
- **`com.analysis.tool.cli.RemoteWorkflowCli`**: Non-interactive remote workflow (`--url=`) that clones, analyses and optionally generates/executes.
- **`com.analysis.tool.workflow.AnalysisWorkflow`**: The single orchestrator shared by all entry points; owns the preflight → classify → diff → impact → select → generate → execute sequence and produces the consolidated PHASE-11 report.
- **`com.analysis.tool.remote.RemoteRepository`**: Validates and clones a Git URL into a temporary workspace, reports the default branch and latest/previous commits, redacts credentials, and deletes the workspace on close.
- **`com.analysis.tool.impact.ChangedFileClassifier`**: Path-based classification of changed files, both the coarse analysable/unanalysable decision and the eight multi-language reporting categories (A–H).
- **`com.analysis.tool.impact.ChangeImpactAnalyzer`**: Coordinates Git diffing, member change detection, call-graph traversal, test discovery, and explanation chain generation.
- **`com.analysis.tool.graph.DependencyGraph`**: Represents nodes (`MemberId`) and edges (callee-to-caller invocations and constructor instantiations).
- **`com.analysis.tool.execution.IsolatedRevisionCheckout`**: Clones the target revision into a temporary directory so the user's workspace is never dirtied during execution.
- **`com.analysis.tool.execution.SurefireReportParser`**: Parses Maven Surefire XML reports to report individual test outcomes.
- **`com.analysis.tool.preflight.Preflight`**: Validates project prerequisites before analysis.
- **`com.analysis.tool.eval`**: Standalone ground-truth evaluation framework computing precision and recall against mutation faults.

---

## Technology Stack

- **Language:** Java 17 (compiled to target 17)
- **Build Tool:** Apache Maven 3.8+
- **Git Interaction:** Eclipse JGit `6.10.0.202406032230-r`
- **Source Code Parsing:** JavaParser & Symbol Solver `3.26.2`
- **Unit Testing:** JUnit Jupiter `5.10.2`
- **Packaging:** Maven Shade Plugin `3.5.2` (executable uber-jar) and Exec Maven Plugin `3.2.0`

---

## Prerequisites

1. **Java Development Kit (JDK):** Version 17 or higher (`java -version`).
2. **Apache Maven:** Version 3.8 or higher (`mvn -version`).
3. **Git:** Installed and available on system PATH (`git --version`).
4. **Target Sample Repository:** A valid Git repository containing a Maven project (e.g., the bundled sample project).

---

## Installation & Setup

1. **Clone the Repository:**
   ```bash
   git clone https://github.com/Chandan-1818/code-change-check.git
   cd code-change-check
   ```

2. **Configure Sample Project Path (Configurable):**
   The application does not require hardcoded local paths. You can point the demo and tests to your local sample repository using any of the following mechanisms:

   - **Option 1: Environment Variable (Recommended for CLI / CI):**
     - Windows (PowerShell):
       ```powershell
       $env:RTS_SAMPLE_PROJECT = "D:/PROJECT/SAMPLE-PROJECT/sample-project"
       ```
     - Windows (cmd):
       ```cmd
       set RTS_SAMPLE_PROJECT=D:/PROJECT/SAMPLE-PROJECT/sample-project
       ```
     - Linux / macOS:
       ```bash
       export RTS_SAMPLE_PROJECT=/path/to/sample-project
       ```

   - **Option 2: System Property (Maven flag):**
     ```bash
     -Drts.sample=/path/to/sample-project
     ```

   - **Option 3: Default Fallback:**
     If neither is set, the system looks for the canonical local sample directory at `D:/PROJECT/SAMPLE-PROJECT/sample-project`.

---

## Maven Build Instructions

Build and package the application using standard Maven commands:

1. **Clean and Compile:**
   ```bash
   mvn -B compile
   ```

2. **Run All Unit and Integration Tests:**
   ```bash
   mvn -B clean test
   ```

3. **Package the Executable JAR:**
   ```bash
   mvn -B package -DskipTests
   ```
   *(Or `mvn -B package` to build and test together).*

   The packaging step generates a self-contained executable shaded JAR:
   `target/CODE-CHANGE-ANALYSIS-1.0-SNAPSHOT.jar`

---

## Live Demo Launch Command

You can run the live demonstration using either the packaged executable JAR or Maven `exec:java`. No manual classpath construction is required.

> **Note on the zero-argument form.** When the JAR is started with **no arguments from an interactive
> terminal**, it now opens the [interactive session](#interactive-session) (a Git-URL prompt and the
> operations menu). In a non-interactive context (output redirected, no console — e.g. CI), the same
> zero-argument invocation still runs the static sample demonstration described below. Use
> `--repo=`, `--url=` or fall back to the explicit `--interactive` flag to force a mode.

### 1. Zero-Argument Live Demo (Static Selection Only)

Runs regression test selection on the sample repository comparing revision **V4** (`a3d0163`) with **V5** (`268a165`):

```bash
java -jar target/CODE-CHANGE-ANALYSIS-1.0-SNAPSHOT.jar
```
*(or via Maven)*:
```bash
mvn exec:java
```

### 2. Live Demo with Test Execution (`--execute`)

Executes both the full test suite and selected tests in an isolated checkout of revision V5, verifying that selected tests pass and measuring test reduction:

```bash
java -jar target/CODE-CHANGE-ANALYSIS-1.0-SNAPSHOT.jar --execute
```

### 3. End-to-End Live Workflow with Named Arguments (T3)

The complete `Git Repository -> Git Diff -> Impact Analysis -> Test Selection -> Execution -> Report`
workflow can be driven against any local Git repository with named arguments. This is the form used
for the T3 demonstration (PowerShell):

```powershell
java -jar target/CODE-CHANGE-ANALYSIS-1.0-SNAPSHOT.jar --repo="D:/PROJECT/SAMPLE-PROJECT/sample-project" --base=a3d0163 --target=268a165 --execute
```

`--repo`, `--base` and `--target` are exactly equivalent to the positional form
`<repoPath> <baseRev> <targetRev>`; all three must be given. The run prints:

1. the file-level Git diff with an `A`/`M`/`D` marker per changed, added and deleted file;
2. the changed members and the impacted methods;
3. the selected regression tests (existing tests and generated tests are listed separately);
4. an isolated execution summary (full suite vs. selected run, test reduction);
5. a consolidated **Final report**.

Add test generation and execution of the generated suite to the same command:

```powershell
java -jar target/CODE-CHANGE-ANALYSIS-1.0-SNAPSHOT.jar --repo="D:/PROJECT/SAMPLE-PROJECT/sample-project" --base=a3d0163 --target=268a165 --generate --out=generated-tests --execute
```

With `--generate`, a JUnit 5 characterization suite is generated for the target revision and fed into
the *same* selection pipeline (it is never a second pipeline). With `--execute` added, generated tests
that were selected are copied into the isolated target-revision checkout and actually compiled and run
by Maven. A generated test is reported as `EXECUTED (confirmed by Maven Surefire)` **only** when the
Surefire XML reports name the individual test; otherwise it is reported as `NOT confirmed by Maven` and
counted as not executed. Generated tests carry a `[generated]` marker throughout the output.

---

## Interactive Session

Running the JAR with **no arguments from an interactive terminal**, or with the explicit
`--interactive` flag at any time, starts a menu-driven session:

```powershell
java -jar target/CODE-CHANGE-ANALYSIS-1.0-SNAPSHOT.jar --interactive
```

The session banner is `CHANGE-SENSITIVE REGRESSION TEST SELECTION SYSTEM`. It then:

1. prompts `Enter Git repository URL (or 'exit' to quit):` and validates the URL (an empty or
   invalid URL re-prompts; `exit` ends the session cleanly);
2. prints `Repository URL received.`, clones the repository into a temporary workspace and displays
   the repository, branch and latest/previous commit information;
3. asks how to compare — `1. Automatically compare the latest two commits.` or
   `2. Enter custom base and target commits.` (a repository with a single commit is explained rather
   than failing);
4. offers six operations:

   | # | Operation |
   |---|---|
   | 1 | Analyze repository changes |
   | 2 | Analyze and select regression tests |
   | 3 | Generate automatic JUnit tests |
   | 4 | Analyze, generate and execute tests |
   | 5 | Full regression testing workflow |
   | 6 | Exit |

Every operation returns to the operations menu, and the loop never crashes on empty input, an invalid
URL, an invalid selection or end-of-input. Operations 4 and 5 print a **security warning** and require
explicit confirmation before Maven runs code from the cloned repository.

---

## Remote Git Repository URL

The whole workflow can be driven non-interactively against a remote URL. The repository is cloned into
a temporary workspace; the local machine's repositories and the remote are never modified:

```powershell
java -jar target/CODE-CHANGE-ANALYSIS-1.0-SNAPSHOT.jar --url=https://github.com/owner/repo.git
```

By default the latest two commits of the default branch are compared. Provide an explicit pair for a
custom comparison:

```powershell
java -jar target/CODE-CHANGE-ANALYSIS-1.0-SNAPSHOT.jar --url=https://github.com/owner/repo.git --base=<baseRev> --target=<targetRev>
```

Add `--generate --execute --yes` to run the full workflow end to end (the clone, the analysis, the
generated suite and Maven execution):

```powershell
java -jar target/CODE-CHANGE-ANALYSIS-1.0-SNAPSHOT.jar --url=https://github.com/owner/repo.git --generate --execute --yes
```

`--execute` on a remote repository runs build scripts and tests from that repository on this machine,
so it requires the explicit `--yes` confirmation. Credentials are only ever taken from a
pre-configured secure mechanism (JGit's credential provider / the environment); the CLI never prompts
for them and never logs them. If the network is unavailable or the URL cannot be cloned, the run fails
with exit code `4` and an actionable message rather than a stack trace.

---

## Command-Line Arguments & Options

### Custom Repository Syntax

```bash
java -jar target/CODE-CHANGE-ANALYSIS-1.0-SNAPSHOT.jar <repoPath> <baseRev> <targetRev> [pairs...] [options]
```

or, equivalently, with named arguments:

```bash
java -jar target/CODE-CHANGE-ANALYSIS-1.0-SNAPSHOT.jar --repo=<repoPath> --base=<baseRev> --target=<targetRev> [pairs...] [options]
```

### Required Arguments (When analyzing custom repositories)

All three of the following are required, in either the positional (`<repoPath> <baseRev> <targetRev>`)
or the named (`--repo= --base= --target=`) form:

| Argument | Description | Example |
|---|---|---|
| `<repoPath>` / `--repo=` | Absolute or relative path to the target Git repository | `D:/PROJECT/SAMPLE-PROJECT/sample-project` |
| `<baseRev>` / `--base=` | Base Git commit hash or branch name | `a3d0163` |
| `<targetRev>` / `--target=` | Target Git commit hash or branch name | `268a165` |

### Remote Repository Syntax

```bash
java -jar target/CODE-CHANGE-ANALYSIS-1.0-SNAPSHOT.jar --url=<GIT_URL> [--base=<baseRev> --target=<targetRev>] [options]
```

| Argument | Description | Default |
|---|---|---|
| `--url=<GIT_URL>` | Remote Git URL (`https`, `http`, `ssh`, scp-like or `file://`) to clone into a temporary workspace | required for this mode |
| `--base=` / `--target=` | Explicit comparison revisions; when omitted the latest two commits of the default branch are used | latest two commits |

`--url=` is handled by `RemoteWorkflowCli` (exit code `4` on a clone/network failure). Unlike the
local `--repo=` form it accepts `--generate`, `--execute` and `--yes`, and it never prompts.

### Optional Arguments & Flags

| Option | Description | Default |
|---|---|---|
| `<sourceFile=testFile>` | Explicit source-to-test file mapping | Auto-discovered by convention |
| `--execute` | Runs tests via Maven in an isolated checkout and reports execution metrics | Disabled (static analysis only) |
| `--max-depth=<n>` | Maximum call hops for impact traversal (`-1` for unlimited) | `-1` (unlimited) |
| `--timeout=<ms>` | Maximum execution timeout for Maven test runs | `300000` (5 minutes) |
| `--generated=<dir>` | Includes generated tests from `<dir>/src/test/java/rts/generated` in the selection | Disabled |
| `--generate` | Generates a JUnit 5 suite for the target revision and includes it in the selection | Disabled |
| `--out=<dir>` | Directory `--generate` writes its suite to | `generated-tests` |
| `--interactive` | Starts the interactive menu session regardless of the other arguments | Disabled |
| `--yes` | Non-interactive confirmation required by `--execute` when the source is a **remote** URL | Disabled |
| `-h`, `--help` | Prints CLI usage guide and options | - |
| `-v`, `--version` | Prints application version | - |

### Exit Codes

| Code | Meaning |
|---|---|
| `0` | Success (including runs that print selection warnings, and analysis runs of the local `SelectTestsCli` that print execution failures) |
| `1` | Invalid arguments (too few arguments, malformed pair, missing named argument, unresolvable revision, invalid URL, `--execute` without `--yes`) |
| `2` | Analysis failed (preflight refusal, missing repository, generation failure) |
| `3` | Test execution failure (`AnalysisWorkflow` / `RemoteWorkflowCli` / `InteractiveCli`) |
| `4` | Repository cloning / network failure (remote URL modes) |

Exit codes `3` and `4` are an extension used by the remote and interactive entry points. The legacy
local `SelectTestsCli` contract is unchanged: it still returns `0` when selection succeeds, even if a
requested execution run reports failing tests (those failures are reported in the output, not the exit
code).

---

## Multi-Language Change Handling

A repository may change files in many languages, but this system only analyses and tests Java. Every
changed path is therefore classified (A–H) and reported with **real** counts:

| Category | Examples | In Java scope? |
|---|---|---|
| A. Java production files | `src/main/java/**/*.java` | Yes |
| B. Java test files | `src/test/java/**/*.java` | Yes |
| C. Build files | `pom.xml`, `mvnw`, `build.gradle`, `.mvn/wrapper/**` | No |
| D. Configuration files | `.xml`, `.properties`, `.yml`, `.yaml`, `.json` | No |
| E. Other language files | `.py`, `.js`, `.jsx`, `.ts`, `.tsx`, `.go`, `.cpp`, `.c`, `.cs`, `.php`, `.rb`, ... | No |
| F. Documentation files | `.md`, `.txt`, `.rst`, `README`, `LICENSE` | No |
| G. Static assets | `.html`, `.css`, `.scss`, images, fonts, media | No |
| H. Binary / unknown files | `.jar`, `.class`, `.zip`, `.pdf`, executables, unknown extensions | No |

Behaviour by change set:

- **Java change present:** the Java pipeline runs normally.
- **Only non-Java, non-build changes** (e.g. a JavaScript, Python, CSS or HTML edit): the system prints
  exactly
  `No supported Java source changes were detected. Java regression test generation was not required. Other-language changes were detected but are outside the current testing scope.`
  and reports the execution status as `NOT RUN`. No Java test is generated and **no failure is
  fabricated** to look like a zero-test success.
- **Build file or resource change** (e.g. `pom.xml`): the change cannot be analysed at member level, so
  selection is conservative — `FALLBACK` confidence, every discovered test selected, and the build
  change is listed in the report.
- **Java test source change:** the added/modified tests are selected directly.
- **New Java production class:** its tests (if any) are selected and, with `--generate`, a
  characterization suite is produced for it.
- **Deleted Java production class:** callers of the removed member are flagged as potentially broken.

Non-Java files are **never** parsed as Java, and the system never claims to have executed tests for an
unsupported language.

---

## Expected Output

### Static Demo Output (`java -jar target/CODE-CHANGE-ANALYSIS-1.0-SNAPSHOT.jar`)

```
================================================================================
 Change-Sensitive Regression Test Selection (RTS) - Live Demonstration
================================================================================
Demo Scenario : Transitive Impact & Regression Test Selection (V4 -> V5)
Target Repo   : D:\PROJECT\SAMPLE-PROJECT\sample-project
Base Commit   : a3d0163 (Version 4: add Calculator.compute() calling Multiplier.multiply())
Target Commit : 268a165 (Version 5: refactor Multiplier.multiply())
--------------------------------------------------------------------------------
Repository     : D:\PROJECT\SAMPLE-PROJECT\sample-project
Compared       : a3d0163 -> 268a165
File pairs     : 3 (auto-discovered)
Confidence     : HIGH
Max depth      : unlimited
Selected tests (2):
  - testCompute
  - testMultiply
Selection reasons:
  - CalculatorTest#testCompute -> CalculatorTest#testCompute -> Calculator#compute -> Multiplier#multiply
  - MultiplierTest#testMultiply -> MultiplierTest#testMultiply -> Multiplier#multiply
```

### Live Execution Output (`java -jar target/CODE-CHANGE-ANALYSIS-1.0-SNAPSHOT.jar --execute`)

```
================================================================================
 Change-Sensitive Regression Test Selection (RTS) - Live Demonstration
================================================================================
Demo Scenario : Transitive Impact & Regression Test Selection (V4 -> V5)
Target Repo   : D:\PROJECT\SAMPLE-PROJECT\sample-project
Base Commit   : a3d0163 (Version 4: add Calculator.compute() calling Multiplier.multiply())
Target Commit : 268a165 (Version 5: refactor Multiplier.multiply())
--------------------------------------------------------------------------------
Repository     : D:\PROJECT\SAMPLE-PROJECT\sample-project
Compared       : a3d0163 -> 268a165
File pairs     : 3 (auto-discovered)
Confidence     : HIGH
Max depth      : unlimited
Selected tests (2):
  - testCompute
  - testMultiply
Selection reasons:
  - CalculatorTest#testCompute -> CalculatorTest#testCompute -> Calculator#compute -> Multiplier#multiply
  - MultiplierTest#testMultiply -> MultiplierTest#testMultiply -> Multiplier#multiply

Executing in an isolated checkout of 268a165 (this launches real Maven builds)...
Target revision : 268a165
Selected tests  : 2 selected
Full suite      : 5 tests, 5 passed, 0 failed, 0 errored, 0 skipped, 5192 ms, build succeeded, compile ok
Selected run    : 2 tests, 2 passed, 0 failed, 0 errored, 0 skipped, 4056 ms, build succeeded, compile ok
Timed out       : no
Test reduction  : 60.0%
Time reduction  : 21.9% (informational - unreliable on small suites due to Maven/JVM startup overhead)
```

**Interpretation:**
- In revision V5, `Multiplier.multiply(int, int)` was modified.
- `MultiplierTest#testMultiply` is selected directly.
- `CalculatorTest#testCompute` is selected transitively because `Calculator.compute(int, int)` invokes `Multiplier.multiply(int, int)`.
- Unrelated tests (`CalculatorTest#testAdd`, `CalculatorTest#testSubtract`) are safely excluded, yielding a **60.0% test count reduction** with zero test failures.

### Consolidated Report (Remote `--url=` / `--interactive`)

The remote and interactive entry points end with a single report whose sections are all computed from
the run (values below are illustrative):

```
================================================================
 CHANGE-SENSITIVE REGRESSION TESTING REPORT
================================================================

REPOSITORY INFORMATION

Repository: https://github.com/owner/repo.git
Branch: main

Base revision: 1a2b3c4
Target revision: 5d6e7f8

PROJECT DISCOVERY

Java project detected: YES
Build system: Maven
Project structure: Single module
Test framework: JUnit 5
Compatibility: SUPPORTED
Maven wrapper: absent

FILE CLASSIFICATION

Total changed files: 4
Java production files: 2
Java test files: 1
Build/configuration files: 0
Other programming language files: 1
Documentation files: 0
Static assets: 0
Binary/unknown files: 0

CHANGE DETECTION

Added files: 1
Modified files: 3
Deleted files: 0

Changed classes: 2
Changed methods: 2
  - com.sample.Multiplier#multiply

DEPENDENCY AND IMPACT ANALYSIS

Directly impacted methods: 1
Transitively impacted methods: 1
Unresolved dependencies: 0
  - (direct) com.sample.MultiplierTest#testMultiply
  - (transitive) com.sample.CalculatorTest#testCompute

REGRESSION TEST SELECTION

Total existing tests: 12
Selected tests: 4 (4 existing, 0 generated)
Excluded tests: 8
Test reduction: 66.7%
Confidence: HIGH
  - com.sample.MultiplierTest#testMultiply

AUTOMATIC TEST GENERATION

Generated test classes: 2
Generated test methods: 6
Generation failures: 0

TEST EXECUTION

Existing selected tests executed: 4
Generated tests executed: 6 / 6 selected

Passed: 10
Failed: 0
Errors: 0
Skipped: 0
Not confirmed: 0

EXECUTION STATUS

SUCCESS

================================================================
 FINAL RESULT
================================================================

The selected regression tests were executed successfully in an isolated checkout.
```

The status line is one of `SUCCESS`, `PARTIAL SUCCESS`, `FAILED` or `NOT RUN`; it is `NOT RUN` when
execution was not requested (or not applicable) and never reports `SUCCESS` if a compilation or a
mandatory execution step failed.

---

## Running Existing Tests

Run tests across the project using Maven:

```bash
# Run all tests (unit + integration)
mvn -B test

# Run only the CLI test suite
mvn test -Dtest="DemoCliTest,SelectTestsCliTest"

# Run fast unit tests (excluding Maven subprocess integration tests)
mvn test -Dtest="!*IntegrationTest,!*ExecuteTest,!FaultInjection*"
```

### Verification Evidence

Real command output for every accepted milestone is committed under
[`docs/verification/`](docs/verification/). The current recorded run of this feature is:

| Evidence | Command |
|---|---|
| `2026-11-02-remote-interactive-focused-tests.txt` | focused new suite (39 tests) |
| `2026-11-02-remote-interactive-mvn-test-full.txt` | `mvn -B clean test` (403 tests) |
| `2026-11-02-remote-interactive-mvn-package.txt` | `mvn -B package` (403 tests + shaded JAR) |
| `2026-11-02-remote-interactive-live-url.txt` | remote `--url=` workflow (`file://`) |
| `2026-11-02-remote-interactive-live-public-url.txt` | remote `--url=` workflow (public GitHub clone) |
| `2026-11-02-remote-interactive-live-interactive.txt` | scripted `--interactive` session |
| `2026-11-02-remote-interactive-live-local-t3.txt` | local T3 `--repo= --execute` regression |
| `2026-11-02-remote-interactive-live-help-version.txt` | `--help` / `--version` |
| `2026-11-02-remote-interactive-verification-summary.txt` | A–J verification checklist |

---

## Distinction of Core Functionalities

To maintain clarity, the system divides responsibilities across distinct modules:

| Functionality | CLI / Module | Purpose | Status |
|---|---|---|---|
| **Live Demonstration** | `DemoCli` | Single-command launch showing transitive selection and execution metrics | **Verified** |
| **Regression Test Selection (RTS)** | `SelectTestsCli` | Static impact analysis selecting existing manual JUnit 5 tests affected by code changes | **Verified** |
| **Test Execution & Metrics** | `TestRunner`, `IsolatedRevisionCheckout` | Executes tests in temporary checkouts to report real execution results and test reductions | **Verified** |
| **Automatic Test Generation** | `GenerateTestsCli`, `TestGenerator` | Deterministic local generation of characterization tests for production code | **Verified (Phase 8B G1-G7)** |
| **Experiment & Evaluation** | `ExperimentCli`, `FaultInjectionEvaluator` | Evaluates precision and recall over commit histories and mutation faults | **Verified (H10)** |
| **Remote Repository Workflow** | `RemoteWorkflowCli`, `RemoteRepository` | Clones a Git URL and runs the full workflow non-interactively (`--url=`) | **Verified** |
| **Interactive Session** | `InteractiveCli` | Menu-driven end-to-end session (`--interactive`) | **Verified** |
| **Multi-Language Classification** | `ChangedFileClassifier` | Classifies changed files into categories A–H and gates the Java scope | **Verified** |
| **Workflow Orchestration** | `AnalysisWorkflow` | Single shared pipeline producing the consolidated report | **Verified** |

---

## Security & Reliability

The remote and interactive entry points are hardened against the risks of running untrusted code:

- **URL validation.** A repository URL is validated before any filesystem or network use; unsupported
  or malformed URLs are rejected with exit code `1`.
- **Safe temporary workspaces.** A remote clone goes into a dedicated `rts-remote-*` directory in the
  system temp location. Project names are sanitised (path traversal such as `../../etc/passwd` is
  neutralised) so a remote name can never escape the workspace.
- **Credential handling.** Credentials are taken only from a pre-configured secure mechanism (JGit's
  credential provider / the environment). The CLI never prompts for a password and never writes one to
  the log; any credential embedded in a URL is redacted before it is printed.
- **Explicit confirmation before execution.** Running Maven build scripts and tests from a cloned
  repository executes that repository's code on this machine, so `--execute` prints a security warning
  and requires `--yes` (remote CLI) or an explicit confirmation (interactive session).
- **Network and Maven timeouts.** The clone is bounded by a configurable timeout, and Maven runs are
  bounded by `--timeout`; a timeout terminates the process tree and is reported distinctly.
- **Safe failure and cleanup.** Clone failures, invalid revisions and analysis failures are reported
  with actionable messages and distinct exit codes instead of stack traces; temporary workspaces are
  deleted on completion (and on failure), and the caller's repository and the remote are never
  modified, reset, cleaned or pushed to.
- **Original repository protection.** Analysis and isolated execution both run against a temporary
  checkout, so a user's working tree is never dirtied.

---

## Current Limitations

1. **Project Type Support:** The target repository must be a single-module Java project managed with Apache Maven and JUnit 5. Multi-module Maven builds and JUnit 4 suites are rejected by preflight validation.
2. **Class Name Resolution in Graph:** Method call edges match member names and arities; full callee type resolution is not yet implemented (callee class names in un-instantiated static calls remain `UNKNOWN`).
3. **Non-Java Changes:** Changes to non-Java files cannot be analysed at member level. A `pom.xml`/resource change triggers a safe `FALLBACK` selection that selects all discovered tests; a change set with *only* other-language/documentation/asset files is reported and not tested, since those languages are outside the current Java testing scope.
4. **Isolated Execution Requirements:** Test execution (`--execute`) requires Maven (`mvn`) to be installed and available on the system PATH.
5. **Remote Cloning:** A remote URL requires network access and JGit support for the transport; private repositories require credentials to be configured through the secure mechanism described above.
6. **No External Database or Dashboard:** Results are output directly to the console or Markdown/JSON/CSV reports; no GUI dashboard or database backend is implemented.
7. **No External AI:** Test generation is fully local and deterministic — no external, cloud or paid AI service and no remote code generation is used.

---

## Project Structure

```
d:/PROJECT/CODE-CHANGE-ANALYSIS
|-- pom.xml                               # Maven project configuration (shade & exec plugins)
|-- README.md                             # Project overview and execution guide
|-- docs/
|   |-- STATUS.md                         # Evidence-based implementation status register
|   |-- TEST_GENERATION_SPEC.md           # Specification for Phase 8B test generation
|   |-- handover/                         # Architectural blueprints and roadmap documents
|   `-- verification/                     # Command output logs verifying test results
|-- src/
|   |-- main/java/com/analysis/tool/
|   |   |-- cli/
|   |   |   |-- DemoCli.java              # Live demo main entry point (T1)
|   |   |   |-- SelectTestsCli.java       # RTS selection CLI entry point
|   |   |   |-- GenerateTestsCli.java     # Test generation CLI entry point
|   |   |   |-- InteractiveCli.java       # Interactive menu session (PHASE 1)
|   |   |   `-- RemoteWorkflowCli.java    # Non-interactive remote workflow (PHASE 12)
|   |   |-- remote/                       # Remote Git URL validation and temporary cloning
|   |   |-- workflow/                     # Shared orchestrator and consolidated report
|   |   |-- eval/                         # Ground-truth precision & recall evaluation
|   |   |-- execution/                    # Isolated execution checkout & Surefire report parsing
|   |   |-- experiment/                   # Multi-revision experiment runner
|   |   |-- git/                          # JGit revision diffing and source retrieval
|   |   |-- graph/                        # Reverse call graph, nodes, and member identities
|   |   |-- impact/                       # AST change detection, impact analysis, test discovery,
|   |   |                                 # and multi-language file classification
|   |   |-- metrics/                      # Test and time reduction calculations
|   |   |-- parser/                       # JavaParser AST visitor and method model
|   |   |-- preflight/                    # Target repository preflight validation
|   |   `-- testgen/                      # Local JUnit characterization test generator
|   `-- test/java/com/analysis/tool/      # Complete JUnit test suite
`-- target/
    `-- CODE-CHANGE-ANALYSIS-1.0-SNAPSHOT.jar  # Shaded executable JAR with all dependencies
```
