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
9. [Command-Line Arguments & Options](#command-line-arguments--options)
10. [Expected Output](#expected-output)
11. [Running Existing Tests](#running-existing-tests)
12. [Distinction of Core Functionalities](#distinction-of-core-functionalities)
13. [Current Limitations](#current-limitations)
14. [Project Structure](#project-structure)

---

## Problem Statement

In modern continuous integration (CI/CD) pipelines, regression testing verifies that new code changes do not break existing software features. However:
- Running the full test suite on every commit is computationally expensive and delays feedback to developers.
- Skipping tests ad-hoc risks missing regression defects when changes propagate through multi-layer method call chains.
- Coarse file-level selection over-selects tests whenever unrelated methods in the same file are changed.

This project addresses these challenges by computing fine-grained, member-level change diffs and traversing the static dependency call graph to select only the tests impacted by the change (both directly and transitively).

---

## How the System Works

1. **Preflight Repository Validation (`Preflight`):**  
   Validates the target repository prior to analysis. Refuses unsupported setups (non-Git directories, linked worktrees, multi-module Maven projects, JUnit 4 suites) with actionable diagnostics, and flags potential edge-hiding libraries (Spring, Mockito, Lombok, reflection) with `REDUCED` confidence.

2. **Git Change Detection (`GitAnalyzer`):**  
   Uses Eclipse JGit to analyze the diff between two Git revisions (`baseRevision` and `targetRevision`), identifying changed, added, or deleted files.

3. **AST Parsing & Member-Level Change Detection (`JavaParserAnalyzer`, `MemberChangeDetector`):**  
   Parses source files into Abstract Syntax Trees using JavaParser. Detects additions, modifications, and removals of methods, constructors, fields, and record/annotation headers, computing member identities with parameter arity.

4. **Static Reverse Call Graph Construction (`DependencyGraphBuilder`):**  
   Constructs a directed call graph mapping methods and constructors to their callers across production and test code.

5. **Transitive Impact Analysis (`ChangeImpactAnalyzer`, `ImpactAnalyzer`):**  
   Performs a reverse breadth-first search (BFS) from the modified members through the call graph to find all reachable test methods. Unreached modified methods are flagged as warnings, and call chains are recorded as selection reasons.

6. **Isolated Test Execution & Metrics (`IsolatedRevisionCheckout`, `TestRunner`):**  
   When execution is requested (`--execute`), tests run inside an isolated temporary Git checkout of the target revision. Real Maven subprocesses execute the full suite and the selected suite, parsing Surefire XML reports to measure exact pass/fail counts, test count reduction (%), and execution time reduction (%).

---

## Existing Architecture & Components

```
+---------------------------------------------------------------------------------+
|                                 CLI Entry Points                                |
|   DemoCli (Live Demo)  |  SelectTestsCli (RTS Engine)  |  GenerateTestsCli      |
+---------------------------------------------------------------------------------+
          |                                  |                         |
          v                                  v                         v
+-------------------+              +-------------------+     +--------------------+
| Preflight Guard   |              | GitAnalyzer       |     | TestGenerator      |
| (Validation GAP-19)|             | (JGit Diffing)    |     | (Local Generation) |
+-------------------+              +-------------------+     +--------------------+
          |                                  |
          +-----------------+----------------+
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

---

## Command-Line Arguments & Options

### Custom Repository Syntax

```bash
java -jar target/CODE-CHANGE-ANALYSIS-1.0-SNAPSHOT.jar <repoPath> <baseRev> <targetRev> [pairs...] [options]
```

### Required Positional Arguments (When analyzing custom repositories)

| Argument | Description | Example |
|---|---|---|
| `<repoPath>` | Absolute or relative path to the target Git repository | `/path/to/sample-project` |
| `<baseRev>` | Base Git commit hash or branch name | `a3d0163` |
| `<targetRev>` | Target Git commit hash or branch name | `268a165` |

### Optional Arguments & Flags

| Option | Description | Default |
|---|---|---|
| `<sourceFile=testFile>` | Explicit source-to-test file mapping | Auto-discovered by convention |
| `--execute` | Runs tests via Maven in an isolated checkout and reports execution metrics | Disabled (static analysis only) |
| `--max-depth=<n>` | Maximum call hops for impact traversal (`-1` for unlimited) | `-1` (unlimited) |
| `--timeout=<ms>` | Maximum execution timeout for Maven test runs | `300000` (5 minutes) |
| `--generated=<dir>` | Includes generated tests from `<dir>/src/test/java/rts/generated` | Disabled |
| `-h`, `--help` | Prints CLI usage guide and options | - |
| `-v`, `--version` | Prints application version | - |

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

---

## Current Limitations

1. **Project Type Support:** The target repository must be a single-module Java project managed with Apache Maven and JUnit 5. Multi-module Maven builds and JUnit 4 suites are rejected by preflight validation.
2. **Class Name Resolution in Graph:** Method call edges match member names and arities; full callee type resolution is not yet implemented (callee class names in un-instantiated static calls remain `UNKNOWN`).
3. **Non-Java Changes:** Changes to non-Java files (e.g., `pom.xml`, configuration resources) cannot be analyzed at member level; they trigger a safe `FALLBACK` selection that selects all discovered tests.
4. **Isolated Execution Requirements:** Test execution (`--execute`) requires Maven (`mvn`) to be installed and available on the system PATH.
5. **No External Database or Dashboard:** Results are output directly to the console or Markdown/JSON/CSV reports; no GUI dashboard or database backend is implemented.

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
|   |   |   `-- GenerateTestsCli.java     # Test generation CLI entry point
|   |   |-- eval/                         # Ground-truth precision & recall evaluation
|   |   |-- execution/                    # Isolated execution checkout & Surefire report parsing
|   |   |-- experiment/                   # Multi-revision experiment runner
|   |   |-- git/                          # JGit revision diffing and source retrieval
|   |   |-- graph/                        # Reverse call graph, nodes, and member identities
|   |   |-- impact/                       # AST change detection, impact analysis, test discovery
|   |   |-- metrics/                      # Test and time reduction calculations
|   |   |-- parser/                       # JavaParser AST visitor and method model
|   |   |-- preflight/                    # Target repository preflight validation
|   |   `-- testgen/                      # Local JUnit characterization test generator
|   `-- test/java/com/analysis/tool/      # Complete JUnit test suite
`-- target/
    `-- CODE-CHANGE-ANALYSIS-1.0-SNAPSHOT.jar  # Shaded executable JAR with all dependencies
```
