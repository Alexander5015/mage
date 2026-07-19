# XMage backend benchmarks

This opt-in module establishes repeatable performance gates for backend changes. It is deliberately excluded from the default Maven reactor; enable it with the `benchmarks` profile. The module compiles to XMage's Java 8 bytecode target, although the benchmark process may run on a newer compatible JDK.

## Prerequisites

- Bash, Git worktree support, Maven, and a working JDK.
- The normal `Mage.Tests` fixtures, configuration, decks, card database inputs, and plugin build outputs.
- A quiet machine with stable power and thermal settings for claim-bearing comparisons.
- No `xmage.network.nocompress` setting in `JAVA_TOOL_OPTIONS`, `JDK_JAVA_OPTIONS`, or `_JAVA_OPTIONS`.

JMH always runs with `Mage.Tests` as its working directory because the deterministic game fixture uses the real server-side test harness. That harness resolves `config/config.xml`, `RB Aggro.dck`, and plugin data relative to `Mage.Tests`.

## Commands

Run a short functional smoke check from anywhere in the repository:

```bash
scripts/benchmarks/smoke.sh
```

Smoke uses one fork, one warmup iteration, and one 100 ms measurement iteration. It proves that every benchmark can execute and emit JMH JSON plus an environment manifest. It is explicitly a non-claim run and cannot establish an improvement.

Compare two Git refs with full AB/BA measurements:

```bash
scripts/benchmarks/compare-refs.sh <baseline-ref> <candidate-ref>
```

The comparison runner resolves both refs to immutable commits, creates detached temporary worktrees, builds both versions, and resolves one absolute Java executable for all four runs and both CLIs.
It always runs the complete protected suite. Scoped claim runs are intentionally unsupported because omitting a policy-covered workload would make the comparison fail closed.

## Deterministic workloads

The tracked policy covers exactly eleven average-time workloads:

1. Full `Game.copy()`.
2. `GameState.copy()`.
3. `Battlefield.copy()`.
4. Compress a real two-player `GameView`.
5. Decompress a precompressed `GameView`.
6. Compress a small control-message payload.
7. Decompress a precompressed control payload.
8. Java-serialize a `GameView`.
9. Java-deserialize a pre-serialized `GameView`.
10. Java-serialize the control payload.
11. Java-deserialize the control payload.

The real payload fixture always contains two players and 18 battlefield permanents, disables initial library shuffling, and embeds a canonical fingerprint covering representative `Game` and `GameView` state. A full comparison serializes one fixture with the baseline code and passes that exact file to every baseline and candidate fork. Every load recomputes and verifies the baseline fingerprint, so serialization changes cannot silently drop state; random UUIDs, wall-clock fields, and map iteration order also cannot change the measured input between pairings. Decompression and deserialization inputs are prepared outside the measured operation. The control payload helps distinguish fixed protocol overhead from costs that scale with the game view.

## What counts as a guaranteed improvement

The full runner executes `AB baseline`, `AB candidate`, `BA candidate`, and `BA baseline`, each with three forks, five one-second warmup iterations, ten one-second measurement iterations, one thread, and the JMH GC profiler. These minimums and the required shared-fixture JVM argument are encoded in `benchmark-policy.json` and checked against actual JMH metadata. Equally weak runs cannot become claim-bearing merely because their settings match; longer confirmatory runs may exceed the minimums. The reversed second pair reduces sensitivity to machine drift and run order.

Every policy-covered benchmark must pass in both pairings:

- average operation time improves by at least 5%, using unrounded scores;
- the candidate confidence interval is strictly below the baseline interval;
- normalized allocation (`gc.alloc.rate.norm`) regresses by no more than 2%; and
- benchmark keys, modes, units, parameters, environment fields, and policy coverage match exactly.

Missing metrics, incompatible environments, dirty worktrees, extra or missing benchmarks, malformed data, and non-finite values fail closed. “Guaranteed improvement” therefore means only that the candidate satisfied these benchmark-scoped gates on the recorded host and JVM. It is not a universal latency guarantee for every game, workload, machine, or deployment.

The runner also requires the complete `Mage.Benchmarks` Git tree to match between refs. Benchmark, fixture, policy, and comparator changes must be landed and validated separately before that commit becomes the baseline for a production optimization. Both refs run focused fixture, payload round-trip, and copy-independence tests before measurements begin; the copy checks require distinct objects, representative state preservation, and mutation isolation for `Game`, `GameState`, and `Battlefield`.

## Results and cleanup

Local artifacts are written beneath the ignored directory `.benchmark-results/<run-id>/`. Smoke writes `results.json`, `manifest.json`, and `jmh.log`. Full comparisons add build logs, four ordered run directories, `comparison.log`, and `comparison-report.json`.

Successful full comparisons remove their two detached worktrees and leave the result directory. Any build, benchmark, environment, or policy failure retains both the results and every worktree that was created. The failure message prints the exact paths and cleanup commands, shaped like:

```bash
git -C /path/to/mage worktree remove --force /tmp/xmage-benchmarks.XXXXXX/baseline
git -C /path/to/mage worktree remove --force /tmp/xmage-benchmarks.XXXXXX/candidate
rmdir /tmp/xmage-benchmarks.XXXXXX
```

Inspect the retained JSON and logs before running those exact commands.

The runner always loads `ManifestMain`, `CompareMain`, and `benchmark-policy.json` from the baseline ref. A candidate therefore cannot weaken the gate used to judge itself. When the benchmark contract genuinely needs to change, land and validate that policy change separately, then use the resulting commit as the baseline for the later optimization.
