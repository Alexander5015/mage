# Backend Benchmark Foundation Design

Date: 2026-07-18
Status: Proposed for implementation
Branch: `backend-benchmarks`

## Context

This repository is now a personal XMage development line. Completed changes are merged into local `master`, while their topic branches are retained so individual commits can later be reconstructed as possible upstream pull requests.

The long-term program has two related goals:

1. improve backend performance with evidence that each accepted performance change improves the workload it targets; and
2. separate server contracts and transport from the legacy client so a modern client can replace it without embedding Swing or the current JBoss Remoting implementation in the server boundary.

XMage currently has a few ignored or manually timed performance tests, including game-state copying and serialization. They are useful clues, but they do not control JVM warmup, report allocation, quantify uncertainty, or compare two revisions under the same environment. Optimizing against them would make performance claims fragile.

The first milestone therefore builds a trustworthy measurement foundation. It changes no production behavior and makes no production optimization.

## Goals

- Add a Java 8-compatible, repeatable microbenchmark suite for important backend work.
- Keep benchmarks outside the normal `mvn test` lifecycle.
- Establish deterministic fixtures for game copying and client-bound payload processing.
- Record latency or throughput together with allocation and garbage-collection data.
- Compare a baseline ref and candidate ref on the same machine and JVM.
- Reject a claimed performance change unless it clears a meaningful, explicit acceptance policy.
- Produce machine-readable results suitable for later automation and trend analysis.
- Establish serialization and payload baselines before server contracts or transport are extracted.

## Non-goals

- Optimizing game copying, compression, serialization, callbacks, or transport in this milestone.
- Designing the replacement client protocol or modern client.
- Running benchmarks as unit tests or as part of every normal reactor build.
- Claiming that a result on one machine proves an identical speedup on every machine or every workload.
- Committing machine-specific benchmark result numbers as a universal baseline.
- Replacing macro-level server load, soak, or production telemetry tests that will be added later.

## Meaning of a guaranteed improvement

Performance guarantees in this program are benchmark-scoped. A change described as a performance improvement must name the representative benchmark it targets and pass the comparison policy against its baseline revision on the same host and JVM.

The default acceptance policy is:

- the benchmark's primary score improves by at least 5%;
- the JMH score confidence intervals do not overlap and favor the candidate;
- normalized allocation per operation does not regress by more than 2%; and
- no other benchmark declared as protected by the change regresses beyond its configured tolerance.

Average-time benchmarks are better when lower; throughput benchmarks are better when higher. Each tracked benchmark has a policy entry declaring its mode, primary metric, minimum improvement, protected secondary metrics, and permitted regression. A benchmark may override the defaults only through a reviewed policy change that explains the workload-specific tradeoff.

These rules deliberately reject ambiguous or noise-sized wins. They guarantee that accepted changes improve their declared, deterministic workload under the recorded comparison environment; they do not overstate that result as a universal hardware guarantee.

## Architecture

### Opt-in Maven module

Add a standalone `Mage.Benchmarks` Maven module, activated by a root `benchmarks` profile. It is not added to the root's default `<modules>` list. A normal reactor build therefore retains its existing modules and does not discover or execute JMH benchmarks.

The benchmark module follows the OpenJDK JMH standalone-project pattern:

- `org.openjdk.jmh:jmh-core:1.37`;
- `org.openjdk.jmh:jmh-generator-annprocess:1.37` as the annotation processor; and
- a shaded executable `Mage.Benchmarks/target/benchmarks.jar` whose main class is JMH's runner.

All benchmark and support classes compile with the repository's Java 8 source and target settings. Verification checks that generated class files have major version 52.

### Deterministic fixtures

The first benchmarks need realistic game objects without starting a network server. Existing server-side tests already create deterministic games through `CardTestPlayerBase`. Under the `benchmarks` profile, `Mage.Tests` will attach its compiled tests as a `tests` classifier. `Mage.Benchmarks` will depend on that classifier and explicitly declare any test-harness dependencies it needs.

This reuses the same fixture construction used by correctness tests while leaving the default `Mage.Tests` artifact and lifecycle unchanged. Benchmark state setup occurs outside timed JMH methods. A setup failure or a fixture that does not satisfy explicit invariants aborts the run rather than producing a misleading measurement.

Fixture definitions are named and versioned in source. Initial payload sizes use fixed cards, zones, turn state, and player counts; they do not use random card selection, wall-clock values, live databases, or remote services.

### Initial benchmark families

#### Game and game-state copying

Measure operations that are already recognized as backend costs:

- `Game.copy()` for a small deterministic two-player state;
- `GameState.copy()` for the same state; and
- battlefield reset/copy behavior represented by the existing ignored state-copying test.

Each invocation copies from a prepared source fixture that the benchmark treats as read-only. JMH consumes the result through `Blackhole` so dead-code elimination cannot remove the operation. Fixture invariants are checked between iterations to detect accidental source mutation.

#### Callback payload compression

Measure `CompressUtil.compress` and `CompressUtil.decompress` separately for representative client-bound data. The primary initial payload is a deterministic `GameView`; a smaller control payload captures fixed overhead.

Compression benchmarks prepare the uncompressed object before timing. Decompression benchmarks prepare the `ZippedObjectImpl` before timing. Round-trip correctness remains a unit-test concern, but benchmark setup also validates the decoded payload's key invariants before measurement begins.

Benchmark setup requires compression to be enabled and fails if `xmage.network.nocompress` is present. This prevents an external JVM property from silently converting the compression benchmark into a pass-through benchmark.

#### Client-view serialization

Measure Java serialization and deserialization separately for the same representative `GameView` and callback payloads. Streams and buffers required by the operation are created in the timed method when production creates them per operation; fixture construction is not timed.

This family establishes the cost of the current client boundary before later contract extraction or transport replacement. Future protocol implementations will be required to compare equivalent payload semantics, not an artificially reduced object graph.

### Run profiles

Two run profiles serve different purposes:

- **Smoke:** one fork with short warmup and measurement, used only to verify discovery, setup, and result generation. Smoke results cannot support performance claims.
- **Comparison:** three forks, five one-second warmup iterations, and ten one-second measurement iterations by default, with one benchmark thread. JMH's GC profiler is enabled to collect normalized allocation and GC metrics.

The comparison settings are stored in versioned configuration and copied into each result manifest. A longer confirmatory run can increase forks or iteration duration without weakening the policy.

### Results and environment manifest

JMH writes JSON for every run. A companion manifest records at least:

- git ref and full commit ID;
- dirty-worktree state;
- benchmark command and configuration;
- JVM vendor, runtime version, and VM name;
- operating system and architecture;
- CPU model and logical processor count; and
- run timestamp.

Raw results and manifests live below an ignored results directory. They are retained locally on success and failure, but machine-specific scores are not committed. The repository commits benchmark definitions, comparison policy, comparator tests, and small synthetic JSON fixtures only.

### Baseline/candidate comparison

A repository command accepts two refs, creates isolated temporary Git worktrees, builds both with the benchmark profile, and runs the same benchmark selection using the current JVM. To reduce execution-order and thermal bias, a comparison consists of two pairings in AB/BA order: baseline then candidate, followed by candidate then baseline. The candidate must pass the policy in both pairings. It refuses to start if a ref cannot be resolved or if the requested output location would overwrite an existing run.

The comparison tool reads JMH JSON and the two manifests. It fails closed when:

- benchmark names, modes, parameters, or units differ;
- a policy-required primary or secondary metric is missing;
- JVM or CPU identity differs materially between the two runs;
- a score contains invalid numeric data;
- the minimum improvement is not reached;
- confidence intervals overlap; or
- a protected metric exceeds its regression tolerance.

The report includes both pairings' baseline score, candidate score, percent change, confidence bounds, allocation change, policy thresholds, and a pass/fail reason for every benchmark. The process exits nonzero if any required benchmark fails in either pairing.

The benchmark-foundation commit itself becomes the first measurable baseline. Earlier commits do not contain the harness and therefore cannot be compared automatically without backporting that harness. Future optimization branches compare their parent or local `master` against the candidate.

### Error handling and cleanup

- Invalid benchmark fixtures stop during setup with a specific invariant failure.
- Build or benchmark failures preserve logs and partial results.
- Temporary comparison worktrees are removed after a successful comparison.
- Failed comparisons retain enough metadata to reproduce the run and print explicit cleanup instructions.
- Cleanup never targets the repository root or an unresolved path.

## Repository layout

The implementation is expected to add or modify these areas:

```text
pom.xml                                  opt-in benchmarks profile
Mage.Tests/pom.xml                       profile-only tests classifier
Mage.Benchmarks/
  pom.xml                                JMH executable module
  README.md                              run and interpretation guide
  src/main/java/.../benchmark/           JMH benchmarks and fixtures
  src/main/java/.../comparison/          result comparator and manifest support
  src/test/java/.../comparison/          comparator tests
  src/test/resources/...                 synthetic JMH result fixtures
  benchmark-policy.json                  tracked acceptance thresholds
scripts/benchmarks/                      smoke and ref-comparison entry points
.gitignore                               local benchmark results
```

Exact package names may be refined in the implementation plan, but production classes in `Mage`, `Mage.Common`, and `Mage.Server` are not changed in this milestone.

## Verification

Implementation is complete only when all of the following pass:

1. The normal full reactor still builds and tests without activating or listing `Mage.Benchmarks`.
2. The benchmark profile builds the shaded executable under Java 8-compatible bytecode.
3. JMH discovers every initial benchmark.
4. A smoke run completes and writes valid JSON plus an environment manifest.
5. Comparator unit tests cover improvement, insufficient improvement, overlapping confidence intervals, allocation regression, missing metrics, mismatched benchmark metadata, and environment mismatch.
6. A full local baseline-versus-identical-ref comparison fails the minimum-improvement gate as expected, demonstrating that the comparator does not manufacture a win from noise.
7. A synthetic known-improvement fixture passes, while each protected-regression fixture fails with the expected explanation.

## Relationship to the broader backend program

This milestone is the measurement layer for later work. Subsequent architecture milestones are expected to proceed on separate retained branches:

1. optimize measured backend hot paths;
2. extract client/server contracts from the mixed `Mage.Common` module;
3. isolate callback production and delivery behind headless interfaces;
4. place JBoss Remoting behind a transport adapter and introduce a replacement gateway; and
5. build a modern client against the stable contract.

Serialization and payload benchmarks protect the boundary while it is being extracted. Game-copy benchmarks protect core simulation work while internal data structures are refined. Macro-level concurrency and end-to-end server benchmarks will be designed once the narrow measurements are stable.

## Alternatives considered

### JUnit timing assertions

Rejected because JVM warmup, dead-code elimination, GC, scheduling noise, and ad hoc iteration counts make timing assertions unreliable and flaky.

### Only macro-level server load tests

Rejected for the first milestone because they are valuable for capacity validation but poor at isolating the cause of small backend changes. They will complement rather than replace JMH later.

### Profiler snapshots without a comparison gate

Rejected because profiles identify where time is spent but do not prove that a candidate revision improved a stable workload or avoided allocation regressions.

### Committed machine-specific baseline scores

Rejected because absolute results age with hardware, JVM, and host load. The benchmark definitions and policy are portable; baseline and candidate scores must be collected together in the comparison environment.

## Branch and integration policy

The implementation remains on `backend-benchmarks` until verification passes. It is then merged into local `master`, and the topic branch is retained. Later performance or decoupling work begins from the newly merged local `master` on a new named branch.

Because local `master` intentionally contains personal commits not present upstream, a future upstream proposal should create a fresh branch from `upstream/master` and cherry-pick only the relevant isolated commits. This keeps personal integration history and potential pull-request history independently manageable.
