package org.mage.benchmark;

import mage.game.Game;
import mage.game.GameState;
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
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class GameCopyBenchmark {
    private Game game;
    private GameState gameState;
    private int battlefieldSize;

    @Setup(Level.Trial)
    public void setUp() throws Exception {
        game = DeterministicGameFixture.create().getGame();
        gameState = game.getState();
        battlefieldSize = game.getBattlefield().getAllPermanents().size();
    }

    @Benchmark
    public void copyGame(Blackhole blackhole) {
        blackhole.consume(game.copy());
    }

    @Benchmark
    public void copyGameState(Blackhole blackhole) {
        blackhole.consume(gameState.copy());
    }

    @Benchmark
    public void copyBattlefield(Blackhole blackhole) {
        blackhole.consume(game.getBattlefield().copy());
    }

    @TearDown(Level.Iteration)
    public void verifySourceWasNotMutated() {
        if (game.getBattlefield().getAllPermanents().size() != battlefieldSize) {
            throw new IllegalStateException("Copy benchmark mutated the source fixture");
        }
    }
}
