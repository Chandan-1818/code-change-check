# SOURCE_CODE_INVENTORY.md

Project: **Change-Sensitive Regression Test Selection Using Code Impact Analysis**
Module: `com.analysis.tool:CODE-CHANGE-ANALYSIS` 1.0-SNAPSHOT (Java 17, Maven)

## 0. Evidence basis (read first)

- Compiled from the **68 files attached in the Claude Project** (26 main classes, 36 test files, `pom.xml`, `.gitignore`, `CLAUDE.md`, synopsis, blueprint, roadmap). Nothing was compiled or executed. The repository on disk was **not** inspected, so folders such as `.git/`, `target/`, `.vscode/`, `docs/` are not evidenced.
- Test-method counts are **static counts of `@Test` annotations** (89 across 35 test classes, plus 1 helper class). They are not pass counts.
- TRAE must re-verify this inventory against the real tree with the commands in `SETUP_AND_EXECUTION_GUIDE.md` section 3.

## 1. Folder structure (as evidenced)

```
CODE-CHANGE-ANALYSIS/
├── .gitignore                         (target/, .vscode/)
├── pom.xml
├── CLAUDE.md                          (operating rules)
├── MCA_Project_Synopsis_UPDATED.docx  (academic authority)
├── project_blueprint_UPDATED.md       (technical design + gap register)
├── PROJECT_ROAD_MAP_UPDATED.md        (only active roadmap)
├── src/main/java/com/analysis/tool/
│   ├── App.java
│   ├── cli/            SelectTestsCli.java
│   ├── execution/      TestRunner.java, TestRunResult.java
│   ├── experiment/     ExperimentCli, ExperimentRunner, ExperimentExecutor,
│   │                   FaultInjectionRunner, GrownSampleFaults
│   ├── git/            GitAnalyzer.java
│   ├── graph/          DependencyGraph, DependencyGraphBuilder, MethodNode
│   ├── impact/         ChangeImpactAnalyzer, ImpactAnalyzer, ImpactSet,
│   │                   MethodChangeDetector, MethodChangeResult,
│   │                   NonMethodChangeDetector, FileMapping, FileMappingDiscoverer,
│   │                   TestImpactMapper, TestSelectionReport
│   ├── metrics/        EvaluationMetrics.java
│   └── parser/         JavaParserAnalyzer.java, MethodInfo.java
└── src/test/java/com/analysis/tool/
    ├── AppTest.java
    ├── cli/            SelectTestsCli{,Warning,Removed,AutoDiscover,AutoDiscoverTempRepo,Execute}Test
    ├── execution/      TestRunnerIntegrationTest
    ├── experiment/     ExperimentCliTest, ExperimentCliExecuteTest, ExperimentRunnerTest,
    │                   ExperimentExecutorTest, LargerSampleHistoryTest,
    │                   FaultInjectionRunnerTest, FaultInjectionGrownSampleTest
    ├── git/            GitAnalyzerTest, GitAnalyzerListFilesTest
    ├── graph/          DependencyGraphBuilderIntegrationTest
    ├── impact/         (14 classes, see section 5)
    ├── metrics/        EvaluationMetricsIntegrationTest
    ├── parser/         JavaParserAnalyzerTest
    └── testsupport/    PinnedSampleCheckout.java   (helper, not a test)
```

**Not present (planned only):** packages `preflight`, `testgen`, `web`, `persistence`; folders `docs/`, `docs/archive/`, `docs/verification/`, `experiments/`, `generated-tests/`, `src/main/resources/static/`.

**Archived files named in `CLAUDE.md` but not attached:** `PROJECT_ROAD_MAP.md`, `project_road_map_2.md`, `project_blueprint.md`. Do not plan from them.

## 2. Maven configuration (`pom.xml`, exact)

| Item | Value |
|---|---|
| groupId / artifactId / version | `com.analysis.tool` / `CODE-CHANGE-ANALYSIS` / `1.0-SNAPSHOT` |
| Packaging | default (`jar`) |
| `project.build.sourceEncoding` | UTF-8 |
| `maven.compiler.source` / `target` | 17 / 17 |
| Leftover | `<url>http://www.example.com</url>` with a FIXME comment |

**Dependencies**

| groupId:artifactId | Version | Scope |
|---|---|---|
| org.junit.jupiter:junit-jupiter | 5.10.2 | test |
| org.eclipse.jgit:org.eclipse.jgit | 6.10.0.202406032230-r | compile |
| com.github.javaparser:javaparser-symbol-solver-core | 3.26.2 | compile (brings javaparser-core transitively) |

**Plugins** (declared only under `pluginManagement`, i.e. version locks, not configuration): maven-clean 3.1.0, maven-resources 3.0.2, maven-compiler 3.8.0, maven-surefire 3.2.5, maven-jar 3.0.2, maven-install 2.5.2, maven-deploy 2.8.2, maven-site 3.7.1, maven-project-info-reports 3.0.0.

**Observations**
- No Spring Boot, JPA, exec, shade or assembly configuration. `App.main` is a stub (GAP-21).
- No compiler `release` flag; Java 17 comes from the `source`/`target` properties.
- No dependency on any AI, HTTP or cloud client (relevant to the "fully local" generator rule; recheck before finishing any generator work).

## 3. Main classes

Legend: **dep** = classes it uses.

### 3.1 `com.analysis.tool.git`
**`GitAnalyzer`** (JGit wrapper; constructor throws `IOException` if `<path>/.git` does not exist, so worktrees/submodules fail: GAP-19)
| Method | Responsibility |
|---|---|
| `GitAnalyzer(String)` | Opens repo via `FileRepositoryBuilder.setGitDir(path/.git)` |
| `getCurrentBranch()` | `repository.getBranch()` |
| `getChangedFiles(old,new)` | Tree diff with `DiffFormatter`; uses old path for DELETE, new path otherwise. Renames appear as delete+add only if JGit does not detect renames (rename detection is not enabled). Throws if a revision does not resolve |
| `getFileContentAtRevision(rev,path)` | Blob read as UTF-8; `IOException` when rev unresolved or file absent |
| `listFilesAtRevision(rev)` | Recursive tree walk, forward-slash paths |
| `close()` | Must be called (Windows file locks) |

### 3.2 `com.analysis.tool.parser`
**`JavaParserAnalyzer`** | dep: `StaticJavaParser`, `MethodInfo`
- `static parseWithoutComments(src)`: parses and removes every comment. Throws unchecked `ParseProblemException` on unparseable source (no fallback: GAP-12). Uses the default `StaticJavaParser` language level (Java 17 syntax support **unconfirmed**).
- `extractMethodNames(src)`, `extractMethods(src)`: every `MethodDeclaration` (including nested/anonymous) becomes `MethodInfo(name, bodyText, header)` where header = annotations + `getDeclarationAsString(true,true,true)`.

**`MethodInfo`**: value object (`name`, `bodyText`, `declaration`); 2-arg constructor sets declaration to `""`.

### 3.3 `com.analysis.tool.graph`
**`MethodNode`**: identity = `className` + `methodName` (no signature). `toString()` = `Class.method()`.
**`DependencyGraph`**: forward map `callees`, reverse map `callers`.
- `addEdge`, `getCallees`, `getCallers` (exact node), `getAllNodes`.
- `getCallersByMethodName(name)`: scans **all nodes** (O(N) per call) and unions callers of every node with that method name. This bridges `UNKNOWN.x` and `Class.x`.

**`DependencyGraphBuilder`** | dep: `StaticJavaParser`
- `buildFromSource(src)`, `buildFromSources(List<String>)`: for each `ClassOrInterfaceDeclaration`, for each `MethodDeclaration` in it, for each `MethodCallExpr` in the method add edge `Class.method -> UNKNOWN.callee`.
- Not captured: calls inside constructors, initializers, field initializers; enum/record methods (not `ClassOrInterfaceDeclaration`); `new X()` edges; inheritance, override, field edges. Nested classes' methods are found from both outer and inner declarations (outer also records them) `[INFERRED from `findAll` recursion]`. Throws on parse failure.

### 3.4 `com.analysis.tool.impact`
**`MethodChangeDetector`** | dep: `MethodInfo`, `MethodChangeResult`
- `detectChanges(old,new)`: groups methods by name, fingerprints = sorted overload strings of `declaration + "\n" + body`. Any overload change => name "modified". Order-insensitive. (Replaces an earlier last-one-wins map.)

**`MethodChangeResult`**: lists of added, removed, modified, unchanged **names**.

**`NonMethodChangeDetector`** | dep: `JavaParserAnalyzer.parseWithoutComments`
- `hasNonMethodChange(old,new)`: fingerprint sets of package, imports, fields (modifiers+annotations+declarator), constructors, initializer blocks, class/interface headers, enum headers. Returns true only if something in OLD is missing in NEW (change or removal; pure additions are not flagged). Blank old or new source returns false. **Records and annotation declarations are not handled.**

**`ImpactAnalyzer`** | dep: `DependencyGraph`, `MethodNode`, `ImpactSet`
- `analyzeImpact(graph, Set<String> changedNames)`: seeds every graph node whose method **name** is in the set; reverse BFS using `getCallersByMethodName` at every hop; `visited` set (cycle-safe); `MAX_DEPTH = 5` hard-coded, truncation silent (GAP-07). Depth 1 -> `directlyImpacted`, deeper -> `transitivelyImpacted` map. A changed name absent from the graph yields an empty seed (no nodes).

**`ImpactSet`**: `changedMethods`, `directlyImpacted`, `transitivelyImpacted(Map<MethodNode,Integer>)`.

**`FileMapping`**: (sourcePath, testPath) with `equals/hashCode`.
**`FileMappingDiscoverer.discover(List<String>)`**: every `src/main/java/**/Foo.java` becomes `FileMapping(src, src/test/java/**/FooTest.java)` (sorted, TreeSet). The test file need not exist.

**`ChangeImpactAnalyzer`** (orchestrator) | dep: `GitAnalyzer`, `JavaParserAnalyzer`, `MethodChangeDetector`, `NonMethodChangeDetector`, `DependencyGraphBuilder`, `ImpactAnalyzer`, `TestImpactMapper`, `FileMapping`, `TestSelectionReport`, `StaticJavaParser`, `MethodNode`
| Method | Responsibility |
|---|---|
| `analyzeTransitively(repo, old, new, mappings)` | **Main entry.** Per mapping: reads old/new source and new test source (missing = `""`); detects method changes; if `NonMethodChangeDetector` flags the file, ALL of that file's methods (new revision) join the changed set and the path goes to `conservativelyExpandedFiles`; collects `@Test` methods as `MethodNode(className, method)`; builds ONE graph from all new sources + test sources; runs one reverse BFS per changed method **name**; a test reached selects it; a changed name reaching no test is "untested". Removed names are reported only |
| `findImpactedTestsTransitively(...)` | Returns `analyzeTransitively(...).getSelectedTests()` |
| `findImpactedTests(...)` x2 | **Legacy**, direct-call matching via `TestImpactMapper`; throws if the source file is deleted at the new revision (GAP-23) |
| `testNodesReached`, `collectTestNodes` | Private helpers; only `@Test` recognised (GAP-08, GAP-09); one `MethodNode` per (enclosing class, method) |
| `findImpactedTestsForFile` | Private legacy helper |

Semantics to keep in mind: change set is keyed by **name across all files** (a modified `add` in one class selects tests reaching any `add`); test files are visible only through the `Foo -> FooTest` convention at the NEW revision; modified or added test files are not selected on their own (GAP-10).

**`TestSelectionReport`**: `selectedTests` (plain names), `selectedTestIdentifiers` (`Class#method`), `untestedChangedMethods`, `removedMethods`, `conservativelyExpandedFiles`; three constructors kept for compatibility.
**`TestImpactMapper.findTestsExercisingMethod(testSrc, methodName)`**: direct `MethodCallExpr` name match. Superseded; documented gap test exists.

### 3.5 `com.analysis.tool.execution`
**`TestRunner`**: `runFullSuite(path)`, `runSelectedTests(path, Set<String>)`, private `runMaven`, `parseResult`.
- Windows: `cmd /c mvn test [-Dtest=A#m,B#n]`; Unix: `mvn test`. Working dir = given path. Reads stdout+stderr to the end, **no timeout**, then `waitFor`. Parses the **last** `Tests run: X, Failures: Y, Errors: Z, Skipped: W` line. Failures+errors merged into `failed`. Returns zeros if no summary line found. Does not delete `target/` (callers do).
**`TestRunResult`**: totals, passed, failed, skipped, wall-clock ms, `buildSucceeded` (exit code 0).

### 3.6 `com.analysis.tool.metrics`
**`EvaluationMetrics.compute(full, selected)`**: test reduction = (total-selected)/total*100; time reduction analogous; zero denominators give 0.0 (not "n/a" as the blueprint wants). No precision/recall here.

### 3.7 `com.analysis.tool.cli`
**`SelectTestsCli`** | dep: `GitAnalyzer`, `FileMappingDiscoverer`, `ChangeImpactAnalyzer`, `TestRunner`, `EvaluationMetrics`
- `run(args,out,err)`: `--execute` honoured **only as the last argument**; <3 positional args exit 1; malformed `src=test` pair exit 1; no pairs -> auto-discover from the union of files at old and new revisions; prints `Repository`, `Compared`, `File pairs : N[ (auto-discovered)]`, selected tests or `none (no impacted tests found)` / `none` + `WARNING - changed methods not reached by any test`, `Removed methods`; `--execute` runs full+selected in the **given working tree** (GAP-14). Exit codes 0/1/2. Does not print `conservativelyExpandedFiles`. Has its own private `discoverMappings` (duplicate of `ExperimentRunner.discoverMappings`).

### 3.8 `com.analysis.tool.experiment`
- **`ExperimentCli`**: `run(args,out,err)`; flags `--csv=<file>` and `--execute` (mutually exclusive, exit 1), anywhere in args; needs repo + >=2 revisions; exit 2 on exception.
- **`ExperimentRunner`**: `run(repo, revisions)` gives one `Row` per consecutive pair; `toMarkdown`, `toCsv`; package-private static `discoverMappings` (used by `ExperimentExecutor` and `FaultInjectionRunner`).
- **`ExperimentExecutor`**: clones repo to temp dir; per pair checks out new revision (no force), deletes `target/`, runs full + selected via `TestRunner`; pair with empty selection is skipped; `ExecutedRow.isHealthy()`; `toMarkdown`. Analysis reads the original repo, execution runs in the clone.
- **`FaultInjectionRunner`**: clone, per fault checkout base (force), delete `target/`, replace exactly-once text, commit "mutant", analyse base->mutant, run FULL suite, read failing test names from Surefire XML (`TEST-*.xml`, name stripped at `(`); `classify` gives CAUGHT / MISSED / NOT_DETECTED_BY_SUITE; `toMarkdown`, `summary`; `sampleProjectFaults()` (9 faults, V8); `main` (arg `grown` selects 17 faults; exit 2 if any MISSED).
- **`GrownSampleFaults.faults()`**: 17 single-line faults for V16.

### 3.9 Root
**`App`**: Maven "Hello World" stub. No packaged entry point.

### 3.10 Class dependency summary
```
SelectTestsCli ──> GitAnalyzer, FileMappingDiscoverer, ChangeImpactAnalyzer, TestRunner, EvaluationMetrics
ExperimentCli ──> ExperimentRunner, ExperimentExecutor
ExperimentRunner ──> GitAnalyzer, FileMappingDiscoverer, ChangeImpactAnalyzer
ExperimentExecutor ──> ExperimentRunner.discoverMappings, ChangeImpactAnalyzer, TestRunner, EvaluationMetrics, JGit
FaultInjectionRunner ──> ExperimentRunner.discoverMappings, ChangeImpactAnalyzer, TestRunner, GrownSampleFaults, JGit, DOM
ChangeImpactAnalyzer ──> GitAnalyzer, JavaParserAnalyzer, MethodChangeDetector, NonMethodChangeDetector,
                         DependencyGraphBuilder, ImpactAnalyzer, TestImpactMapper(legacy), TestSelectionReport
ImpactAnalyzer ──> DependencyGraph, MethodNode, ImpactSet
DependencyGraphBuilder ──> DependencyGraph, MethodNode
NonMethodChangeDetector ──> JavaParserAnalyzer
MethodChangeDetector ──> MethodInfo, MethodChangeResult
EvaluationMetrics ──> TestRunResult ; TestRunner ──> TestRunResult
```

## 4. Test environment dependencies

| Class of test | Needs |
|---|---|
| **P** pure | nothing external |
| **T** temp repo | JGit only, `@TempDir`, portable |
| **S** sample git | `D:/PROJECT/SAMPLE-PROJECT/sample-project` + pinned hashes (Git only, fast) |
| **M** sample + Maven | S plus real `mvn` subprocesses (slow); needs `mvn` on PATH |

Pinned sample revisions: V1 `f2493c2`, V2 `9ed64fa`, V3 `7584402`, V4 `a3d0163`, V5 `268a165`, V6 `cc0d769`, V7 `bae01d1`, V8 `ebec3c0`, V9 `7b79d9c`, V10 `52db174`, V11 `2a36954`, V12 `bee1bf5`, V13 `d85a3b7`, V14 `efc30d0`, V15 `3337a0b`, V16 `7059f4c`.

Sample production classes seen in tests/faults: Calculator (add, subtract, compute[V3-V15], negate[V6+]), Multiplier (multiply), OrderRepository (save, findTotal), OrderService (placeOrder, getOrderTotal), TaxCalculator (calculateTax), DiscountPolicy (applyDiscount), PricingService (finalPrice), InventoryRepository (setStock, getStock), InventoryService (restock, available), CheckoutService (checkout, quote), App. `[INFERRED from tests; sample repo not inspected]`.

## 5. Test classes and methods (89 `@Test`, statically counted)

| Test class | Env | `@Test` methods |
|---|---|---|
| `AppTest` | P | shouldAnswerWithTrue |
| `git.GitAnalyzerTest` | S | shouldOpenSampleProjectAndReadBranch, shouldDetectChangedFileBetweenVersion1AndVersion2, shouldReadDifferentFileContentAtDifferentRevisions |
| `git.GitAnalyzerListFilesTest` | T | shouldListAllFilesPresentAtRevision |
| `parser.JavaParserAnalyzerTest` | P | shouldExtractMethodNamesFromCalculator, shouldDetectDifferentBodyTextForModifiedMethod, shouldDetectSameBodyTextForUnchangedMethod |
| `graph.DependencyGraphBuilderIntegrationTest` | S | shouldRecordComputeCallsMultiplyEdge |
| `impact.MethodChangeDetectorIntegrationTest` | S | shouldDetectSubtractModifiedAndAddUnchangedAcrossRealCommits |
| `impact.MethodChangeDetectorSafetyTest` | P | changeToOneOverloadIsNotHiddenByTheOther, reorderingOverloadsIsNotAChange, signatureOnlyChangeIsDetected, commentAndWhitespaceOnlyChangeIsNotAChange |
| `impact.NonMethodChangeDetectorTest` | P | changedConstant, removedField, removedImport, classAnnotationAdded, constructorBodyChange (flagged); addedField, addedImport, addedOrRemovedMethod, methodBodyOnly, commentOnly, addedOrDeletedFile (not flagged): 11 total |
| `impact.ChangeImpactAnalyzerNonMethodChangeTest` | T | constantChangeSelectsTestsOfMethodsInTheFile, commentOnlyChangeSelectsNothing |
| `impact.TestImpactMapperIntegrationTest` | S | shouldMapSubtractToTestSubtractOnly, shouldMapAddToTestAddOnly |
| `impact.TestImpactMapperGapIntegrationTest` | S | shouldFailToFindTestComputeAsExercisingMultiply... (documents the legacy limitation; **must keep passing while `TestImpactMapper` exists**) |
| `impact.ImpactAnalyzerIntegrationTest` | S | shouldIdentifyComputeAsDirectlyImpactedWhenMultiplyChanges |
| `impact.ImpactAnalyzerTransitiveTestSelectionIntegrationTest` | S | shouldIdentifyTestComputeAsImpactedWhenMultiplyChangesViaTransitiveChain |
| `impact.ChangeImpactAnalyzerIntegrationTest` | S | shouldSelectOnlyTestSubtractForRealCommitRange, shouldSelectNoTestsWhenComparingRevisionToItself, shouldSelectOnlyTestMultiplyWhenOnlyMultiplierIsAddedAcrossMultipleFiles (all legacy path) |
| `impact.ChangeImpactAnalyzerTransitiveIntegrationTest` | S | shouldSelectDirectAndTransitiveTestsWhenOnlyMultiplyIsModified, shouldSelectOnlyTestComputeWhenComputeIsAdded, shouldSelectNothingWhenComparingRevisionToItself |
| `impact.ChangeImpactAnalyzerReportIntegrationTest` | S | shouldReportNegateAsUntestedAndSelectNoTests..., shouldReportNothingUntestedWhenEveryChangedMethodIsCovered, shouldReportCoveredAndUncoveredChangesTogether, shouldReportNothingWhenComparingRevisionToItself |
| `impact.ChangeImpactAnalyzerIdentifierIntegrationTest` | S | shouldReturnClassQualifiedIdentifiersForSelectedTests |
| `impact.ChangeImpactAnalyzerOrderIntegrationTest` | S | shouldSelectDirectAndTransitiveOrderTests..., shouldSelectNothingWhenComparingRevisionToItself, shouldSelectBothOrderTestsWhenOrderClassesAreNewlyAdded |
| `impact.ChangeImpactAnalyzerDeletedFileIntegrationTest` | T | deletedSourceFile_doesNotThrow, deletedTestFile_doesNotThrow_andChangedMethodIsReportedUntested, removedMethod_isReported_andDoesNotAffectSelection |
| `impact.FileMappingDiscovererTest` | P | shouldPairSourceWithConventionalTestPath, shouldIgnoreTestFilesBuildFilesAndNonJavaSources, shouldStillPairSourceWhoseTestFileDoesNotExist |
| `cli.SelectTestsCliTest` | mixed | exitCode1AndUsage (P), exitCode1MalformedPair (P), exitCode0ListSelectedTests (S), exitCode2WhenRepositoryDoesNotExist (P) |
| `cli.SelectTestsCliWarningTest` | S | shouldWarnAboutNegate..., shouldNotWarnWhenEveryChangedMethodIsCovered, shouldReportNoImpactedTestsAndNoWarningWhenNothingChanged |
| `cli.SelectTestsCliRemovedTest` | T | shouldListRemovedMethodWithoutWarningOrSelection |
| `cli.SelectTestsCliAutoDiscoverTest` | S | ...V4ToV5 (3 pairs), ...UntestedMethod, ...OrderChainForV7ToV8 (5 pairs), shouldNotAutoDiscoverWhenExplicitPairsAreGiven |
| `cli.SelectTestsCliAutoDiscoverTempRepoTest` | T | sourceDeletedAtNewRevision_..., sourceAddedAtNewRevision_... |
| `cli.SelectTestsCliExecuteTest` | **M** | shouldExecuteAndReportTestReduction (passes a V8 clone as repo path to dodge GAP-14), shouldSkipExecutionWhenNoTestsAreSelected (S, fast) |
| `execution.TestRunnerIntegrationTest` | **M** | shouldRunAllSevenTestsInTheFullSuite, shouldRunOnlyTheSelectedTests |
| `metrics.EvaluationMetricsIntegrationTest` | **M** | shouldComputeTestReductionForRealV4ToV5Selection (asserts 7 and 2 tests, 500/7 %) |
| `experiment.ExperimentRunnerTest` | S | shouldProduceOneRowPerConsecutiveRevisionPair, shouldMatchSelectionsProvenElsewhereForKnownRanges, shouldRenderMarkdownAndCsv |
| `experiment.ExperimentCliTest` | S (+T for CSV) | shouldPrintMarkdownTableForSampleHistory, shouldWriteCsvFileWhenRequested, exitCode1WhenFewerThanTwoRevisions, exitCode2WhenRepositoryDoesNotExist |
| `experiment.ExperimentCliExecuteTest` | **M** | shouldPrintExecutedTableWithReductionAtEachRevision, shouldRejectCsvTogetherWithExecute (fast) |
| `experiment.ExperimentExecutorTest` | **M** | shouldRunSuitesAtEachRevisionAndSkipEmptySelections, shouldRenderExecutedAndSkippedRowsAsMarkdown (P) |
| `experiment.LargerSampleHistoryTest` | S | shouldSelectExpectedTestsAndReportUntestedAndRemovedMethods, shouldRenderTheModifiedMethodRowInMarkdown |
| `experiment.FaultInjectionRunnerTest` | **M** | shouldClassifyOutcomes (P), shouldRenderTableAndSummaryFromSyntheticResults (P), shouldSelectEveryFailingTestForEachInjectedFault (M, 9 faults at V8) |
| `experiment.FaultInjectionGrownSampleTest` | **M** | shouldDefineSeventeenFaultsWithUniqueMethodNames (P), shouldSelectEveryFailingTestForEachInjectedFault (M, 17 faults at V16) |
| `testsupport.PinnedSampleCheckout` | helper | `cloneAt(sourceRepo, revision, emptyDir)`: JGit clone + checkout |

Tests that do not exist (coverage holes): unit tests for `ImpactAnalyzer` (cycles, depth cap), `DependencyGraph` (symmetry, duplicates), `EvaluationMetrics` (synthetic 100/20/50s/10s case), `TestRunner.parseResult`, `GitAnalyzer.getChangedFiles` on temp repos, parse-failure behaviour, Java 17 syntax, `@Nested`, lifecycle methods, build/resource-file changes, anything for test generation.

## 6. Configuration files
`pom.xml` (above); `.gitignore` (`target/`, `.vscode/`; **missing** `generated-tests/` and `docs/` decisions). No `application.properties`, no CI workflow, no `settings.xml`, no `.env` evidenced.

## 7. Existing documentation
| File | Role | Status of content |
|---|---|---|
| `CLAUDE.md` | Operating rules | Current; sections 1-10 |
| `MCA_Project_Synopsis_UPDATED.docx` | Academic authority (sections 1-23 frozen; section 24 = status annex dated 2026-10-25) | Section 24 outdated: says overloads collapse, signature/fields undetected |
| `project_blueprint_UPDATED.md` | Technical design, gap register (GAP-01..26), decisions D-01..D-24 | Package map omits `NonMethodChangeDetector`; GAP-01/02/03/20 stale |
| `PROJECT_ROAD_MAP_UPDATED.md` | Only active roadmap (phases 0-23, 8A, 8B; TS-01..26) | TS-06, TS-16 "none" is stale; H3/H4 partly done |
| Javadoc | `ChangeImpactAnalyzer` header partly stale and has a garbled sentence about name collisions | |
