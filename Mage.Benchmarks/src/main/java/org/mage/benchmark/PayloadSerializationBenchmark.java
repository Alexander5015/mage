package org.mage.benchmark;

import mage.view.GameView;
import org.mage.benchmark.fixture.DeterministicGameFixture;
import org.mage.benchmark.support.JavaSerialization;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class PayloadSerializationBenchmark {
    private GameView gameView;
    private List<String> controlPayload;
    private byte[] serializedGameView;
    private byte[] serializedControlPayload;

    @Setup(Level.Trial)
    public void setUp() throws Exception {
        gameView = DeterministicGameFixture.create().getGameView();
        controlPayload = Arrays.asList("game-update", "turn-1", "player-a");
        serializedGameView = JavaSerialization.serialize(gameView);
        serializedControlPayload = JavaSerialization.serialize(controlPayload);
        if (serializedGameView.length == 0 || serializedControlPayload.length == 0) {
            throw new IllegalStateException("Serialized payload is empty");
        }
        Object restoredGameView = JavaSerialization.deserialize(serializedGameView);
        if (!(restoredGameView instanceof GameView)
                || !Arrays.equals(
                        serializedGameView,
                        JavaSerialization.serialize(restoredGameView))) {
            throw new IllegalStateException("Serialized GameView did not round-trip exactly");
        }
        Object restoredControlPayload = JavaSerialization.deserialize(serializedControlPayload);
        if (!controlPayload.equals(restoredControlPayload)) {
            throw new IllegalStateException("Serialized control payload did not round-trip exactly");
        }
    }

    @Benchmark
    public byte[] serializeGameView() throws Exception {
        return JavaSerialization.serialize(gameView);
    }

    @Benchmark
    public Object deserializeGameView() throws Exception {
        return JavaSerialization.deserialize(serializedGameView);
    }

    @Benchmark
    public byte[] serializeControlPayload() throws Exception {
        return JavaSerialization.serialize(controlPayload);
    }

    @Benchmark
    public Object deserializeControlPayload() throws Exception {
        return JavaSerialization.deserialize(serializedControlPayload);
    }
}
