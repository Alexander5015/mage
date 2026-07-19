package mage.server.game;

import mage.abilities.Ability;
import mage.cards.Card;
import mage.constants.PhaseStep;
import mage.constants.Zone;
import mage.game.command.Emblem;
import mage.game.command.emblems.MomirEmblem;
import mage.game.permanent.Permanent;
import mage.game.stack.StackAbility;
import mage.view.GameView;
import org.junit.Test;
import org.mage.test.serverside.base.CardTestPlayerBase;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
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

    @Test
    public void directRenderingDoesNotMutateSourceGame() throws IOException {
        setStrictChooseMode(true);
        setStopAt(1, PhaseStep.END_TURN);
        execute();

        addEmblem(playerA, new MomirEmblem());
        Emblem emblem = currentGame.getState().getCommand().stream()
                .filter(Emblem.class::isInstance)
                .map(Emblem.class::cast)
                .filter(commandObject -> commandObject.getName().equals("Emblem Momir"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Momir emblem was not added to the game"));
        Ability ability = emblem.getAbilities().get(0);
        currentGame.getStack().push(currentGame, new StackAbility(ability, playerA.getId()));

        UUID userA = UUID.randomUUID();
        GameView defensive = GameViewBuilder.fromDefensiveCopyForPlayer(currentGame, playerA.getId(), userA);
        byte[] before = serialize(currentGame);

        GameView direct = GameViewBuilder.renderPlayerView(currentGame, playerA.getId(), userA);

        assertArrayEquals(before, serialize(currentGame));
        assertEquals(
                normalizeVolatilePriorityTimeSavedTimeMs(defensive),
                normalizeVolatilePriorityTimeSavedTimeMs(direct)
        );
    }

    @Test
    public void directPriorityPlayerRenderingDoesNotReplaceCastSourceState() throws IOException {
        addCard(Zone.BATTLEFIELD, playerA, "Island", 3);
        addCard(Zone.GRAVEYARD, playerA, "Think Twice", 1);

        setStrictChooseMode(true);
        setStopAt(1, PhaseStep.END_TURN);
        execute();

        currentGame.getState().setPriorityPlayerId(playerA.getId());
        Object costs = playerA.getCastSourceIdCosts();
        Object manaCosts = playerA.getCastSourceIdManaCosts();
        Object alternateMana = playerA.getCastSourceIdWithAlternateMana();
        byte[] before = serialize(currentGame);

        GameViewBuilder.renderPlayerView(currentGame, playerA.getId(), UUID.randomUUID());

        assertSame(costs, playerA.getCastSourceIdCosts());
        assertSame(manaCosts, playerA.getCastSourceIdManaCosts());
        assertSame(alternateMana, playerA.getCastSourceIdWithAlternateMana());
        assertArrayEquals(before, serialize(currentGame));
    }

    @Test
    public void directRenderingDoesNotInitializeVolosJournalHintState() throws IOException {
        addCard(Zone.BATTLEFIELD, playerA, "Island", 3);
        addCard(Zone.HAND, playerA, "Volo, Itinerant Scholar", 1);
        castSpell(1, PhaseStep.PRECOMBAT_MAIN, playerA, "Volo, Itinerant Scholar");

        setStrictChooseMode(true);
        setStopAt(1, PhaseStep.POSTCOMBAT_MAIN);
        execute();

        Permanent journal = getPermanent("Volo's Journal", playerA.getId());
        String notedTypesKey = "notedTypes_" + journal.getId() + '_' + journal.getZoneChangeCounter(currentGame);
        assertNull(currentGame.getState().getValue(notedTypesKey));

        byte[] beforePlayer = serialize(currentGame);
        GameViewBuilder.renderPlayerView(currentGame, playerA.getId(), UUID.randomUUID());
        assertNull(currentGame.getState().getValue(notedTypesKey));
        assertArrayEquals(beforePlayer, serialize(currentGame));

        byte[] beforeWatcher = serialize(currentGame);
        GameViewBuilder.renderWatcherView(currentGame, UUID.randomUUID());
        assertNull(currentGame.getState().getValue(notedTypesKey));
        assertArrayEquals(beforeWatcher, serialize(currentGame));
    }

    @Test
    public void directRenderingDoesNotInitializeDynamicHintWatcherState() throws IOException {
        addCard(Zone.BATTLEFIELD, playerA, "Belbe, Corrupted Observer", 1);
        addCard(Zone.BATTLEFIELD, playerA, "Korvold, Gleeful Glutton", 1);

        setStrictChooseMode(true);
        setStopAt(1, PhaseStep.END_TURN);
        execute();

        byte[] beforePlayer = serialize(currentGame);
        GameViewBuilder.renderPlayerView(currentGame, playerA.getId(), UUID.randomUUID());
        assertArrayEquals(beforePlayer, serialize(currentGame));

        byte[] beforeWatcher = serialize(currentGame);
        GameViewBuilder.renderWatcherView(currentGame, UUID.randomUUID());
        assertArrayEquals(beforeWatcher, serialize(currentGame));
    }

    @Test
    public void directPlayerRenderingDoesNotInitializeOrdinaryCardState() throws IOException {
        Card card = addFreshOrdinaryCardToGraveyard();
        assertNull(currentGame.getState().getCardStateIfExists(card.getId()));
        byte[] before = serialize(currentGame);

        GameViewBuilder.renderPlayerView(currentGame, playerA.getId(), UUID.randomUUID());

        assertNull(currentGame.getState().getCardStateIfExists(card.getId()));
        assertArrayEquals(before, serialize(currentGame));
    }

    @Test
    public void directWatcherRenderingDoesNotInitializeOrdinaryCardState() throws IOException {
        Card card = addFreshOrdinaryCardToGraveyard();
        assertNull(currentGame.getState().getCardStateIfExists(card.getId()));
        byte[] before = serialize(currentGame);

        GameViewBuilder.renderWatcherView(currentGame, UUID.randomUUID());

        assertNull(currentGame.getState().getCardStateIfExists(card.getId()));
        assertArrayEquals(before, serialize(currentGame));
    }

    private Card addFreshOrdinaryCardToGraveyard() {
        setStrictChooseMode(true);
        setStopAt(1, PhaseStep.END_TURN);
        execute();

        addCard(Zone.GRAVEYARD, playerA, "Forest", 1);
        Card card = getGraveCards(playerA).get(0);
        currentGame.cheat(
                playerA.getId(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.singletonList(card),
                Collections.emptyList(),
                Collections.emptyList()
        );
        return card;
    }

    private static byte[] serialize(Object object) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(object);
        }
        return bytes.toByteArray();
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
