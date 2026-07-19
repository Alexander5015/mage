package org.mage.benchmark.fixture;

import mage.game.Game;
import mage.view.GameView;
import org.junit.Test;

import java.util.concurrent.FutureTask;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class DeterministicGameFixtureTest {

    @Test
    public void createsStableTwoPlayerPayload() throws Exception {
        DeterministicGameFixture.Snapshot snapshot = DeterministicGameFixture.create();
        Game game = snapshot.getGame();
        GameView gameView = snapshot.getGameView();

        assertNotNull(game);
        assertNotNull(gameView);
        assertEquals(2, game.getPlayers().size());
        assertEquals(18, game.getBattlefield().getAllPermanents().size());
        assertEquals(2, gameView.getPlayers().size());
        assertEquals(18, gameView.getPlayers().get(0).getBattlefield().size()
                + gameView.getPlayers().get(1).getBattlefield().size());
    }

    @Test
    public void createsFixtureFromJmhStyleWorkerThread() throws Exception {
        FutureTask<DeterministicGameFixture.Snapshot> task =
                new FutureTask<>(DeterministicGameFixture::create);
        Thread worker = new Thread(task, "benchmark-worker");

        worker.start();
        DeterministicGameFixture.Snapshot snapshot = task.get();

        assertNotNull(snapshot.getGame());
        assertEquals("benchmark-worker", worker.getName());
    }
}
