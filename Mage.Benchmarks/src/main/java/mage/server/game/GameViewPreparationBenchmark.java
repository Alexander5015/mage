package mage.server.game;

import mage.game.Game;
import mage.util.ThreadUtils;
import mage.view.GameView;
import org.mage.benchmark.fixture.DeterministicGameFixture;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class GameViewPreparationBenchmark {
    private static final UUID WATCHER_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000000");

    private DeterministicGameFixture.Snapshot fixture;
    private Game game;
    private UUID playerId;
    private Thread workerThread;
    private String originalThreadName;

    @Setup(Level.Trial)
    public void setUp() throws Exception {
        workerThread = Thread.currentThread();
        originalThreadName = workerThread.getName();
        workerThread.setName(ThreadUtils.THREAD_PREFIX_GAME + " benchmark worker");
        try {
            fixture = DeterministicGameFixture.create();
            game = fixture.getGame();
            playerId = game.getPlayers().values().iterator().next().getId();
        } catch (Exception | Error e) {
            workerThread.setName(originalThreadName);
            throw e;
        }
    }

    @Benchmark
    public GameView preparePlayerView() {
        return GameViewBuilder.fromStableGameForPlayer(game, playerId, playerId);
    }

    @Benchmark
    public GameView prepareWatcherView() {
        return GameViewBuilder.fromStableGameForWatcher(game, WATCHER_USER_ID);
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        if (workerThread != null && originalThreadName != null) {
            workerThread.setName(originalThreadName);
        }
    }

    String fixtureFingerprint() {
        return fixture.semanticFingerprint();
    }
}
