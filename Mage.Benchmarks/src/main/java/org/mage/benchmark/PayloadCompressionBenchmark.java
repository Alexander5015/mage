package org.mage.benchmark;

import mage.remote.traffic.ZippedObject;
import mage.utils.CompressUtil;
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

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class PayloadCompressionBenchmark {
    private GameView gameView;
    private List<String> controlPayload;
    private Object compressedGameView;
    private Object compressedControlPayload;

    @Setup(Level.Trial)
    public void setUp() throws Exception {
        if (System.getProperty("xmage.network.nocompress") != null) {
            throw new IllegalStateException("Compression benchmark requires xmage.network.nocompress to be absent");
        }
        gameView = DeterministicGameFixture.create().getGameView();
        controlPayload = Arrays.asList("game-update", "turn-1", "player-a");
        compressedGameView = CompressUtil.compress(gameView);
        compressedControlPayload = CompressUtil.compress(controlPayload);
        if (!(compressedGameView instanceof ZippedObject)
                || !(compressedControlPayload instanceof ZippedObject)) {
            throw new IllegalStateException("Compression is disabled");
        }
    }

    @Benchmark
    public Object compressGameView() {
        return CompressUtil.compress(gameView);
    }

    @Benchmark
    public Object decompressGameView() {
        return CompressUtil.decompress(compressedGameView);
    }

    @Benchmark
    public Object compressControlPayload() {
        return CompressUtil.compress(controlPayload);
    }

    @Benchmark
    public Object decompressControlPayload() {
        return CompressUtil.decompress(compressedControlPayload);
    }
}
