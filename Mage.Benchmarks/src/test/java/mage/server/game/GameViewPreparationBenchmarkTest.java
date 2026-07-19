package mage.server.game;

import mage.util.ThreadUtils;
import mage.view.GameView;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class GameViewPreparationBenchmarkTest {

    @Test
    public void preparesViewsWithoutMutatingFixtureAndRestoresThreadName() throws Exception {
        Thread worker = Thread.currentThread();
        String originalThreadName = worker.getName();
        worker.setName("benchmark-worker");
        GameViewPreparationBenchmark benchmark = new GameViewPreparationBenchmark();
        try {
            boolean setUp = false;
            try {
                benchmark.setUp();
                setUp = true;
                assertTrue(worker.getName().startsWith(ThreadUtils.THREAD_PREFIX_GAME));
                String fingerprint = benchmark.fixtureFingerprint();

                GameView playerView = benchmark.preparePlayerView();
                assertNotNull(playerView);
                assertEquals(fingerprint, benchmark.fixtureFingerprint());

                GameView watcherView = benchmark.prepareWatcherView();
                assertNotNull(watcherView);
                assertEquals(fingerprint, benchmark.fixtureFingerprint());
            } finally {
                if (setUp) {
                    benchmark.tearDown();
                }
            }
            assertEquals("benchmark-worker", worker.getName());
        } finally {
            worker.setName(originalThreadName);
        }
    }
}
