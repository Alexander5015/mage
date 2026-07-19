# Game View Copy Elimination Design

## Context

Server-side game callbacks currently call `Game.copy()` before constructing every personalized `GameView`. The copy protects view construction from concurrent modification and from mutations performed while the view is assembled, but it is expensive: the deterministic benchmark fixture allocates roughly 800 KB for one full game copy. A normal update repeats that work independently for every player and watcher, and prompt callbacks repeat it again.

This branch is deliberately limited to that deep-copy path. It does not change the Java baseline, JBoss Remoting, callback serialization, compression, or the client wire format.

## Goals

- Eliminate full game copies from ordinary game-thread updates, prompts, and watcher notifications.
- Preserve the existing `GameView` object graph and serialized callback format.
- Preserve player-specific visibility, controlled-player behavior, and watcher hand permissions.
- Retain a safe copied path for reconnects and other calls that can occur outside the game thread.
- Demonstrate a statistically credible speedup and allocation reduction with the benchmark comparison tooling.

## Non-goals

- Replacing JBoss Remoting or Java serialization.
- Introducing a new client protocol, delta updates, or a presentation DTO model.
- Changing Java source or runtime compatibility.
- Optimizing the general-purpose `Game.copy()` implementation used by simulations and rollback.

## Selected Approach

Introduce a package-private server-side view builder with two explicit entry points:

1. A stable-game entry point that constructs a view directly from a game whose mutation is paused on the game event thread.
2. A defensive entry point that first copies the game and remains available for reconnect and externally initiated access.

The two entry points must not be selected by a boolean flag; their names must make the concurrency contract visible at each call site. Ordinary callbacks raised synchronously from `GameController` event handlers use the stable-game entry point. Existing off-thread paths keep the defensive entry point.

## View Purity

Direct construction is safe only if building a `GameView` does not modify its source. The current constructor writes names to some stack objects and generates an identifier on a stack ability. Those writes must be replaced with local presentation values or view-constructor inputs.

The implementation must also audit nested view construction for writes to supplied game, state, player, card, permanent, and stack objects. Tests must compare representative source state before and after direct view construction. A direct view must serialize equivalently to the view produced from a defensive copy.

## Callback Integration

`GameController` already receives update and player-query events synchronously while game execution is paused. Its ordinary callback paths will request views through the stable-game builder. This includes:

- player and watcher updates;
- game prompts and selections;
- informational updates sent to other players and watchers;
- game-over callbacks raised from the game event path.

Reconnect initialization and public/external view retrieval can occur from non-game threads and will continue to use defensive copying. This keeps the performance change independent of a larger session-caching or threading redesign.

Compression remains synchronous in `ClientCallback`, so the direct view is fully materialized and serialized before the game event handler returns.

## Compatibility and Correctness

The serialized `GameView` and `GameClientMessage` structures remain unchanged. No client changes are required.

Regression coverage will verify:

- serialized equivalence between defensive and stable-game views;
- no source-game mutation during stable-game view construction;
- correct private-hand visibility for each player;
- correct watcher hand permissions;
- controlled-player and playable-object behavior;
- representative stack objects that previously required constructor mutations.

The safe copied entry point remains the default whenever the caller cannot prove that the source game is stable.

## Benchmark Design

Before the production optimization, add protected JMH workloads for player and watcher view preparation. The benchmark refactor will route through the same package-private builder while retaining the existing defensive-copy behavior, producing a clean baseline commit. The candidate then changes only the stable-game path and its production call sites.

The current comparison policy requires every protected workload to improve, which is inappropriate for a targeted optimization. Before measuring the candidate, extend the baseline policy to distinguish:

- target workloads, which must improve by at least 5% with non-overlapping confidence intervals and no allocation regression; and
- guard workloads, which need not improve but must remain within explicit time and allocation non-regression limits.

Both policy and workload changes must be committed and validated before the performance implementation so the candidate cannot weaken its own gate. The final comparison uses the existing full AB/BA runner on the same host and JVM.

## Success Criteria

- Player and watcher stable-game view workloads improve by at least 5% in both AB and BA pairings.
- Candidate confidence intervals are strictly below baseline intervals for the target workloads.
- Normalized allocation improves or remains within the existing 2% ceiling.
- Guard workloads show no policy-defined regression.
- The focused view tests and the complete Maven reactor pass.
- No normal game-thread callback path performs `Game.copy()` solely to build a `GameView`.

## Rollback Strategy

The change is organized as independently reversible commits: benchmark contract, builder extraction, view-purity fixes, and callback integration. Reverting callback integration restores defensive copying without changing the wire format or removing the new correctness coverage.
