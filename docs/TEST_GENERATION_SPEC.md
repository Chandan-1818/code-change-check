# Automatic Test Generation: Scope Specification (Phase 8B, task G1)

Status: specification written; the version-1 decisions below are the contract for G2-G8.
Nothing here has been executed. See "Status of Phase 8B" at the end.

## 1. Purpose

Generate JUnit 5 characterization tests for the target project locally and
deterministically, as an initial suite and as incremental updates when code
changes. No external AI, remote or cloud service is used.

## 2. Supported targets (version 1)

A method is SUPPORTED when all of these hold:

| Rule | Detail |
|---|---|
| Class | top-level, public, concrete (not interface, not abstract) |
| Method | public, has a body, not generic |
| Return type | non-void; one of int long short byte char boolean double float String, or their boxed forms |
| Parameters | each one of the same set |
| Determinism | body does not mention time, randomness, I/O or threads (token list in TargetDiscoverer) |
| Receiver | static method (no receiver), or instance method whose class is constructible |

A class is CONSTRUCTIBLE when it has no explicit constructor, or a public
constructor whose parameters are supported types or other constructible project
classes (depth limit 3, cycle-safe). Example: OrderService(OrderRepository) is
constructible because OrderRepository has a default constructor.

Everything else is SKIPPED and reported with a reason. Void methods are skipped
in version 1 because there is no return value to record.

## 3. Input selection (proposal, confirm with the guide)

| Type | Seeded pool (fixed order) |
|---|---|
| int, long, short, byte | 0, 1, -1, 2, Integer.MAX_VALUE (skipped if out of range for the type) |
| double, float | 0.0, 1.0, -1.0, 2.5 |
| boolean | false, true |
| char | 'a', 'A', '0' |
| String | null, "", "a", "hello world" |

- Cases per method: cartesian product of the pools, capped at 12, taken in a
  fixed deterministic order.
- Seed: 42. The same source must always give the same tests.
- Constructor arguments for receivers use the first value of each pool.

## 4. Oracle capture (G4, planned)

Each case runs in a separate JVM with a time limit. The recorded return value
or exception class becomes the assertion. Each case runs twice; if the two
results differ, the case is dropped and the method is reported as nondeterministic.

## 5. Output layout

    generated-tests/<project>/<revision>/
        src/test/java/rts/generated/<Class>GeneratedTest.java
        manifest.json

- Package rts.generated; class <Class>GeneratedTest.
- Test method names gen_<Class>_<method>_<n> (unique, because the dependency
  graph matches by method name only).
- Never written into this tool's own src/test/java.
- manifest.json per test: target class, method signature, input set, source
  revision, generator version, seed.

## 6. Incremental update rules

Driven by MethodChangeDetector (added, modified, removed, unchanged):

| Change | Action |
|---|---|
| added | generate new tests |
| modified | re-run existing generated tests; failures are reported as "behaviour changed"; regenerate only after developer acceptance |
| removed | retire tests, keep a record in the manifest |
| unchanged | keep |

## 7. Out of scope for version 1

Void methods, collections, arrays, project-class return values or parameters,
generics, mocks, concurrency, I/O. Characterization tests do not prove the
original behaviour is correct.

## Status of Phase 8B

| Task | Status | Evidence |
|---|---|---|
| G1 specification | Written | this file |
| G2 target discovery | Written, not yet compiled or run | TargetDiscoverer, DiscoveredMethod |
| G8 (part) discovery unit tests | Written, not yet run | TargetDiscovererTest |
| G3-G7 | Not started | none |

No item is Verified until mvn test output is captured on the author's machine.
