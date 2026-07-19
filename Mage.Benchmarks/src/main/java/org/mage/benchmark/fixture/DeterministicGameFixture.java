package org.mage.benchmark.fixture;

import mage.constants.PhaseStep;
import mage.constants.Zone;
import mage.game.Game;
import mage.view.GameView;
import org.mage.test.serverside.base.CardTestPlayerBase;

public final class DeterministicGameFixture extends CardTestPlayerBase {

    private DeterministicGameFixture() {
    }

    public static Snapshot create() throws Exception {
        init();
        DeterministicGameFixture fixture = new DeterministicGameFixture();
        fixture.reset();
        fixture.addCard(Zone.BATTLEFIELD, fixture.playerA, "Mountain", 6);
        fixture.addCard(Zone.BATTLEFIELD, fixture.playerA, "Sol Ring", 2);
        fixture.addCard(Zone.BATTLEFIELD, fixture.playerA, "Grizzly Bears", 1);
        fixture.addCard(Zone.BATTLEFIELD, fixture.playerB, "Mountain", 6);
        fixture.addCard(Zone.BATTLEFIELD, fixture.playerB, "Sol Ring", 2);
        fixture.addCard(Zone.BATTLEFIELD, fixture.playerB, "Grizzly Bears", 1);
        fixture.setStopAt(1, PhaseStep.END_TURN);
        fixture.execute();

        Game game = currentGame.copy();
        GameView gameView = fixture.getGameView(fixture.playerA);
        if (game.getPlayers().size() != 2
                || game.getBattlefield().getAllPermanents().size() != 18) {
            throw new IllegalStateException("Deterministic fixture shape changed");
        }
        return new Snapshot(game, gameView);
    }

    public static final class Snapshot {
        private final Game game;
        private final GameView gameView;

        private Snapshot(Game game, GameView gameView) {
            this.game = game;
            this.gameView = gameView;
        }

        public Game getGame() {
            return game;
        }

        public GameView getGameView() {
            return gameView;
        }
    }
}
