package org.mage.benchmark.fixture;

import mage.game.Game;
import mage.view.GameView;
import org.junit.Test;

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
}
