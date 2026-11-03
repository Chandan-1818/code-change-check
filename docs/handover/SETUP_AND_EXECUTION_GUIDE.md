# SETUP_AND_EXECUTION_GUIDE.md

All commands are for **Git Bash on Windows 11**, run from the project root, unless stated. I could not run any of them. **Every expected result below is an expectation, not an observed result.**

## 1. Prerequisites (roadmap Phase 1)
JDK 17, Maven 3.9+ on PATH (`mvn` must work from `cmd /c mvn`, because `TestRunner` launches it that way), Git CLI + Git Bash. No Spring, DB or Node needed yet.

```bash
java -version
javac -version
mvn -version
git --version
```
Expect Java 17 in both `java` and `mvn -version`. A mismatch (for example Maven using another JDK) will break the Maven-launching tests.

Note (recorded evidence, `docs/STATUS.md`): every baseline recorded so far was produced on JDK 23.0.2 compiling to target 17. A run on an actual **JDK 17** runtime is still **outstanding**; H5 configures the JavaParser language level to Java 17 only (a parse-time setting, not a JDK 17 execution).

## 2. Repository state (read-only)
```bash
git status
git branch --show-current
git log --oneline -15
git remote -v
cat .gitignore
```
Do not stash, reset or clean. If the tree is dirty, report it before doing anything else.

## 3. Verify the sample project and every pinned hash
```bash
SAMPLE="D:/PROJECT/SAMPLE-PROJECT/sample-project"
git -C "$SAMPLE" log --oneline
git -C "$SAMPLE" status --short
for h in f2493c2 9ed64fa 7584402 a3d0163 268a165 cc0d769 bae01d1 ebec3c0 \
         7b79d9c 52db174 2a36954 bee1bf5 d85a3b7 efc30d0 3337a0b 7059f4c; do
  printf "%s : " "$h"; git -C "$SAMPLE" cat-file -t "$h" 2>&1
done
```
Expect 16 lines `commit` (V1..V16 in that order). Any other output means the sample repo or a hash is wrong. Diagnose this **before** suspecting code.

Check that the evidence tree matches the inventory:
```bash
find src -name '*.java' | sort
find src/main -name '*.java' | wc -l
find src/test -name '*.java' | wc -l
grep -rho '@Test' src/test | wc -l
```
Compare with `SOURCE_CODE_INVENTORY.md` (26 main files, 36 test files incl. helper, 89 `@Test`). That inventory is stale: measured on 2026-11-01, the tree has **48 main files, 60 test files (incl. helpers), 191 `@Test`**. Differences are findings to report.

## 4. Build and test (baseline, step H1)

**Compile only**
```bash
mvn -q -DskipTests compile
```

**Fast suite** (everything except the nine classes that launch real Maven builds; Surefire exclusion syntax):
```bash
mkdir -p docs/verification
mvn test -Dtest='!TestRunnerIntegrationTest,!EvaluationMetricsIntegrationTest,!SelectTestsCliExecuteTest,!ExperimentExecutorTest,!ExperimentCliExecuteTest,!FaultInjectionRunnerTest,!FaultInjectionGrownSampleTest,!GeneratedSuiteRunnerIntegrationTest,!GenerateTestsCliRealHistoryTest' 2>&1 | tee docs/verification/baseline-fast-raw.txt | tail -60
```
Expect `BUILD SUCCESS` and `Failures: 0, Errors: 0`, **if** the sample repo is present. Note that some of the excluded classes also contain fast tests (`SelectTestsCliExecuteTest.shouldSkip...`, `ExperimentCliExecuteTest.shouldRejectCsv...`, pure tests inside `FaultInjectionRunnerTest`, `ExperimentExecutorTest`, `FaultInjectionGrownSampleTest`, `GeneratedSuiteRunnerIntegrationTest`, `GenerateTestsCliRealHistoryTest`); the exclusion skips them too. Run them in step 5.

**The nine slow classes** (recorded in `docs/STATUS.md`; each launches nested Maven builds, a few seconds to minutes):
`TestRunnerIntegrationTest`, `EvaluationMetricsIntegrationTest`, `SelectTestsCliExecuteTest`,
`ExperimentExecutorTest`, `ExperimentCliExecuteTest`, `FaultInjectionRunnerTest`,
`FaultInjectionGrownSampleTest`, `GeneratedSuiteRunnerIntegrationTest`, `GenerateTestsCliRealHistoryTest`.

**Slow classes, one at a time**:
```bash
for c in TestRunnerIntegrationTest EvaluationMetricsIntegrationTest SelectTestsCliExecuteTest \
         ExperimentExecutorTest ExperimentCliExecuteTest FaultInjectionRunnerTest \
         FaultInjectionGrownSampleTest GeneratedSuiteRunnerIntegrationTest \
         GenerateTestsCliRealHistoryTest; do
  mvn test -Dtest="$c" 2>&1 | tee "docs/verification/baseline-slow-$c-raw.txt" | tail -25
done
```

**Single test or method**
```bash
mvn -q test -Dtest=ClassName
mvn -q test -Dtest=ClassName#methodName
```

**Full suite before declaring any feature complete**
```bash
mvn test 2>&1 | tee docs/verification/full-raw.txt | tail -60
```

## 5. Recording evidence (the only way a status can become VERIFIED)
For each run, create `docs/verification/<YYYY-MM-DD>-<what>.txt` containing, in this order: the exact command; `git rev-parse --short HEAD`; `git status --short` (empty means clean); the sample project `git log --oneline`; the last 60 lines of output. Example:
```bash
{
  echo "COMMAND: mvn test (fast suite, see guide section 4)"
  echo "COMMIT : $(git rev-parse --short HEAD)"
  echo "DATE   : $(date +%F)"
  echo "DIRTY  : $(git status --short | wc -l) changed paths"
  echo "--- output tail ---"
  tail -60 docs/verification/baseline-fast-raw.txt
} > docs/verification/$(date +%F)-baseline-fast.txt
```
Status tables may be updated only from such files. Do not state completion percentages.

## 6. Reading failures
| Symptom | Likely cause (check first) |
|---|---|
| `Not a Git repository (missing .git directory)` | Sample path wrong or not a normal clone |
| `Could not resolve revision` | Hash missing from the sample repo |
| `File not found at revision` inside sample tests | Wrong hash or repo history rewritten |
| Maven tests report 0 tests, `buildSucceeded=false` | `mvn` not on PATH for `cmd /c`, wrong JDK, offline/compile error in clone |
| Assertion on 7 tests or 5 tests fails | Pinned clone not at the expected revision |
| Windows file lock on temp dir | Leaked `GitAnalyzer` handle or running `mvn` process |
| Unparseable source (H5; was `ParseProblemException`) | Selection falls back conservatively instead of crashing: `Confidence : FALLBACK`, the unparsed files are listed, and every discovered test is selected. JavaParser is configured for Java 17, so records, text blocks and switch expressions parse. A raw `ParseProblemException` or `SourceParseException` escaping the CLI (exit 2) would be a regression. |
Never edit, disable or weaken a test to pass a build.

## 7. Running the tool (CLI)
There is no packaged jar. After `mvn -q -DskipTests compile`, run classes from `target/classes` with the JGit and JavaParser jars on the classpath. The simplest unrecorded-assumption-free way is to let Maven print it:
```bash
mvn -q dependency:build-classpath -Dmdep.outputFile=cp.txt   # needs maven-dependency-plugin from the network
```
(untested; this adds no project dependency). Alternatively use the tests as the entry points (`SelectTestsCliTest`, `ExperimentCliTest`). Typical invocation once a classpath exists:
```bash
java -cp "target/classes;$(cat cp.txt)" com.analysis.tool.cli.SelectTestsCli \
  "D:/PROJECT/SAMPLE-PROJECT/sample-project" a3d0163 268a165
java -cp "target/classes;$(cat cp.txt)" com.analysis.tool.experiment.ExperimentCli \
  "D:/PROJECT/SAMPLE-PROJECT/sample-project" f2493c2 9ed64fa 7584402 --csv=experiments/out.csv
```
Note the Windows classpath separator `;`. `cp.txt` should not be committed. Exit codes: 0 success (warnings included), 1 invalid arguments, 2 analysis failed. Expected for `a3d0163 268a165`: `File pairs : 3 (auto-discovered)`, selected `testCompute` and `testMultiply` (per `SelectTestsCliAutoDiscoverTest`).

Fault-injection (slow, real Maven):
```bash
java -cp "target/classes;$(cat cp.txt)" com.analysis.tool.experiment.FaultInjectionRunner \
  "D:/PROJECT/SAMPLE-PROJECT/sample-project" 7059f4c grown
```
Exit 2 means at least one fault was MISSED.

## 8. Git hygiene (after each step passes its tests)
```bash
git status
git diff --check
git diff
git add path/to/files        # never `git add .`
git commit -m "Descriptive message"
git push origin HEAD
git status
```
Never force-push, `reset --hard`, `clean -fd`, rewrite history or delete branches without explicit permission. Never commit credentials, `.env`, `cp.txt`, `target/`, generated output. Watch Windows line endings (`git diff --check`). First small commit to make: add `generated-tests/` to `.gitignore`.

## 9. Known environment hazards
- The sample path is configurable (system property `rts.sample`, env `RTS_SAMPLE_PROJECT`, default `D:/PROJECT/SAMPLE-PROJECT/sample-project`); GAP-18 is closed (SamplePathsTest). Real-history tests still need the sample repo present. (This replaces the older note that 25 classes hard-code the `D:/` path.)
- Slow tests clone the sample repo into temp directories and delete them recursively; do not run two copies at once.
- Close idle Maven/Java processes when timing; time reductions on a 5-7 test suite are not meaningful.
