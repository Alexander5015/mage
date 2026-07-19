package mage.server.game;

import mage.constants.PhaseStep;
import mage.constants.Zone;
import mage.view.GameView;
import org.junit.Test;
import org.mage.test.serverside.base.CardTestPlayerBase;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GameViewBuilderTest extends CardTestPlayerBase {

    @Test
    public void defensiveCopiesMatchDirectRendering() {
        addCard(Zone.HAND, playerA, "Forest", 1);

        setStrictChooseMode(true);
        setStopAt(1, PhaseStep.END_TURN);
        execute();

        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        UUID userWatcher = UUID.randomUUID();
        playerA.addPermissionToShowHandCards(userWatcher);

        GameView defensive = GameViewBuilder.fromDefensiveCopyForPlayer(currentGame, playerA.getId(), userA);
        GameView rendered = GameViewBuilder.renderPlayerView(currentGame, playerA.getId(), userA);
        assertEquals(
                normalizeVolatilePriorityTimeSavedTimeMs(defensive),
                normalizeVolatilePriorityTimeSavedTimeMs(rendered)
        );
        assertEquals(1, rendered.getMyHand().size());

        GameView playerBView = GameViewBuilder.renderPlayerView(currentGame, playerB.getId(), userB);
        assertEquals(0, playerBView.getMyHand().size());

        GameView defensiveWatcher = GameViewBuilder.fromDefensiveCopyForWatcher(currentGame, userWatcher);
        GameView watcher = GameViewBuilder.renderWatcherView(currentGame, userWatcher);
        assertEquals(
                normalizeVolatilePriorityTimeSavedTimeMs(defensiveWatcher),
                normalizeVolatilePriorityTimeSavedTimeMs(watcher)
        );
        assertEquals(1, watcher.getWatchedHands().size());
    }

    private static String normalizeVolatilePriorityTimeSavedTimeMs(GameView gameView) {
        // PlayerView records its construction time for client timer presentation, so sequential renders differ here.
        return gameView.toJson().replaceAll(
                "\"priorityTimeSavedTimeMs\":\\d+",
                "\"priorityTimeSavedTimeMs\":0"
        );
    }

    @Test
    public void stableEntryPointRequiresGameThread() throws InterruptedException {
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        Thread thread = new Thread(
                () -> {
                    try {
                        GameViewBuilder.fromStableGameForPlayer(currentGame, playerA.getId(), UUID.randomUUID());
                    } catch (Throwable ex) {
                        thrown.set(ex);
                    }
                },
                "game-view-builder-test"
        );

        thread.start();
        thread.join();

        assertTrue(thrown.get() instanceof IllegalArgumentException);
    }
}
