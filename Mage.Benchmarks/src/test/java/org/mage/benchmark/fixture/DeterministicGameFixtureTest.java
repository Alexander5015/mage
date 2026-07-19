package org.mage.benchmark.fixture;

import mage.game.Game;
import mage.game.GameState;
import mage.game.permanent.Battlefield;
import mage.game.permanent.Permanent;
import mage.players.Player;
import mage.view.GameView;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mage.benchmark.support.JavaSerialization;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertThrows;

public class DeterministicGameFixtureTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

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
    public void independentFreshSnapshotsHaveSameSemantics() throws Exception {
        DeterministicGameFixture.Snapshot first = DeterministicGameFixture.create();
        DeterministicGameFixture.Snapshot second = DeterministicGameFixture.create();

        assertEquals(first.semanticFingerprint(), second.semanticFingerprint());
    }

    @Test
    public void claimFixtureLoadsTheExactSameObjectGraph() throws Exception {
        Path fixture = temporaryFolder.getRoot().toPath().resolve("claim-fixture.bin");
        DeterministicGameFixture.writeFresh(fixture);
        String originalProperty = System.getProperty(DeterministicGameFixture.FIXTURE_PATH_PROPERTY);
        try {
            System.setProperty(DeterministicGameFixture.FIXTURE_PATH_PROPERTY, fixture.toString());
            DeterministicGameFixture.Snapshot first = DeterministicGameFixture.create();
            DeterministicGameFixture.Snapshot second = DeterministicGameFixture.create();

            assertEquals(first.semanticFingerprint(), second.semanticFingerprint());
            assertArrayEquals(
                    JavaSerialization.serialize(first.getGameView()),
                    JavaSerialization.serialize(second.getGameView()));
        } finally {
            if (originalProperty == null) {
                System.clearProperty(DeterministicGameFixture.FIXTURE_PATH_PROPERTY);
            } else {
                System.setProperty(DeterministicGameFixture.FIXTURE_PATH_PROPERTY, originalProperty);
            }
        }
    }

    @Test
    public void sharedFixtureRejectsChangedGameSemantics() throws Exception {
        Path fixture = temporaryFolder.getRoot().toPath().resolve("claim-fixture.bin");
        Path changedFixture = temporaryFolder.getRoot().toPath().resolve("changed-fixture.bin");
        DeterministicGameFixture.writeFresh(fixture);
        String originalProperty = System.getProperty(DeterministicGameFixture.FIXTURE_PATH_PROPERTY);
        try {
            System.setProperty(DeterministicGameFixture.FIXTURE_PATH_PROPERTY, fixture.toString());
            DeterministicGameFixture.Snapshot snapshot = DeterministicGameFixture.create();
            Player player = snapshot.getGame().getPlayers().values().iterator().next();
            String originalFingerprint = snapshot.semanticFingerprint();
            player.setLife(player.getLife() - 1, snapshot.getGame(), null);

            assertNotEquals(originalFingerprint, snapshot.semanticFingerprint());
            Files.write(changedFixture, JavaSerialization.serialize(snapshot));
            System.setProperty(DeterministicGameFixture.FIXTURE_PATH_PROPERTY, changedFixture.toString());
            IllegalStateException error = assertThrows(
                    IllegalStateException.class,
                    DeterministicGameFixture::create);
            assertEquals(true, error.getMessage().contains("fingerprint"));
        } finally {
            if (originalProperty == null) {
                System.clearProperty(DeterministicGameFixture.FIXTURE_PATH_PROPERTY);
            } else {
                System.setProperty(DeterministicGameFixture.FIXTURE_PATH_PROPERTY, originalProperty);
            }
        }
    }

    @Test
    public void copyWorkloadsPreserveIndependentState() throws Exception {
        Game game = DeterministicGameFixture.create().getGame();
        int originalTurn = game.getTurnNum();
        int originalPermanents = game.getBattlefield().getAllPermanents().size();

        Game gameCopy = game.copy();
        assertNotSame(game, gameCopy);
        assertNotSame(game.getState(), gameCopy.getState());
        assertEquals(originalTurn, gameCopy.getTurnNum());
        assertEquals(game.getPlayers().size(), gameCopy.getPlayers().size());
        assertEquals(originalPermanents, gameCopy.getBattlefield().getAllPermanents().size());
        Player originalPlayer = game.getPlayers().values().iterator().next();
        Player gameCopyPlayer = gameCopy.getPlayer(originalPlayer.getId());
        assertNotNull(gameCopyPlayer);
        assertNotSame(originalPlayer, gameCopyPlayer);
        assertEquals(originalPlayer.getName(), gameCopyPlayer.getName());
        assertEquals(originalPlayer.getLife(), gameCopyPlayer.getLife());
        assertEquals(originalPlayer.getLibrary().size(), gameCopyPlayer.getLibrary().size());
        assertEquals(originalPlayer.getHand().size(), gameCopyPlayer.getHand().size());
        gameCopy.getState().setTurnNum(originalTurn + 1);
        gameCopy.getBattlefield().clear();
        assertEquals(originalTurn, game.getTurnNum());
        assertEquals(originalPermanents, game.getBattlefield().getAllPermanents().size());

        GameState stateCopy = game.getState().copy();
        assertNotSame(game.getState(), stateCopy);
        assertNotSame(game.getState().getBattlefield(), stateCopy.getBattlefield());
        assertEquals(originalTurn, stateCopy.getTurnNum());
        assertEquals(game.getPlayers().size(), stateCopy.getPlayers().size());
        assertEquals(originalPermanents, stateCopy.getBattlefield().getAllPermanents().size());
        Player stateCopyPlayer = stateCopy.getPlayer(originalPlayer.getId());
        assertNotNull(stateCopyPlayer);
        assertNotSame(originalPlayer, stateCopyPlayer);
        assertEquals(originalPlayer.getName(), stateCopyPlayer.getName());
        assertEquals(originalPlayer.getLife(), stateCopyPlayer.getLife());
        assertEquals(originalPlayer.getLibrary().size(), stateCopyPlayer.getLibrary().size());
        assertEquals(originalPlayer.getHand().size(), stateCopyPlayer.getHand().size());
        stateCopy.setTurnNum(originalTurn + 1);
        stateCopy.getBattlefield().clear();
        assertEquals(originalTurn, game.getState().getTurnNum());
        assertEquals(originalPermanents, game.getBattlefield().getAllPermanents().size());

        Battlefield battlefieldCopy = game.getBattlefield().copy();
        assertNotSame(game.getBattlefield(), battlefieldCopy);
        assertEquals(originalPermanents, battlefieldCopy.getAllPermanents().size());
        Permanent originalPermanent = game.getBattlefield().getAllPermanents().iterator().next();
        UUID permanentId = originalPermanent.getId();
        Permanent copiedPermanent = battlefieldCopy.getPermanent(permanentId);
        assertNotNull(copiedPermanent);
        assertNotSame(originalPermanent, copiedPermanent);
        assertEquals(originalPermanent.getId(), copiedPermanent.getId());
        assertEquals(originalPermanent.getName(), copiedPermanent.getName());
        assertEquals(originalPermanent.getControllerId(), copiedPermanent.getControllerId());
        battlefieldCopy.clear();
        assertEquals(originalPermanents, game.getBattlefield().getAllPermanents().size());
    }

    @Test
    public void createsFixtureFromJmhStyleWorkerThread() throws Exception {
        FutureTask<DeterministicGameFixture.Snapshot> task =
                new FutureTask<>(DeterministicGameFixture::create);
        Thread worker = new Thread(task, "benchmark-worker");

        worker.start();
        try {
            DeterministicGameFixture.Snapshot snapshot = task.get(30, TimeUnit.SECONDS);

            assertNotNull(snapshot.getGame());
            assertEquals("benchmark-worker", worker.getName());
        } finally {
            worker.interrupt();
            worker.join(TimeUnit.SECONDS.toMillis(5));
        }
    }
}
