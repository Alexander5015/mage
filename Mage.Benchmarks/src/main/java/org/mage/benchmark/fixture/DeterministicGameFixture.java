package org.mage.benchmark.fixture;

import mage.constants.PhaseStep;
import mage.constants.Zone;
import mage.game.Game;
import mage.game.permanent.Permanent;
import mage.players.Player;
import mage.util.ThreadUtils;
import mage.view.CardView;
import mage.view.GameView;
import mage.view.PlayerView;
import org.mage.test.serverside.base.CardTestPlayerBase;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class DeterministicGameFixture extends CardTestPlayerBase {
    public static final String FIXTURE_PATH_PROPERTY = "xmage.benchmark.fixture";

    private DeterministicGameFixture() {
    }

    public static Snapshot create() throws Exception {
        String fixturePath = System.getProperty(FIXTURE_PATH_PROPERTY);
        if (fixturePath != null && !fixturePath.trim().isEmpty()) {
            return read(Paths.get(fixturePath));
        }
        return createFresh();
    }

    public static void writeFresh(Path path) throws Exception {
        Snapshot snapshot = createFresh();
        try (ObjectOutputStream output = new ObjectOutputStream(new BufferedOutputStream(
                Files.newOutputStream(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)))) {
            output.writeObject(snapshot);
        }
    }

    private static Snapshot read(Path path) throws IOException, ClassNotFoundException {
        Snapshot snapshot;
        try (ObjectInputStream input = new ObjectInputStream(
                new BufferedInputStream(Files.newInputStream(path)))) {
            Object value = input.readObject();
            if (!(value instanceof Snapshot)) {
                throw new IOException("Benchmark fixture file does not contain a Snapshot: " + path);
            }
            snapshot = (Snapshot) value;
        }
        validate(snapshot);
        return snapshot;
    }

    private static Snapshot createFresh() throws Exception {
        Thread thread = Thread.currentThread();
        String originalThreadName = thread.getName();
        boolean renameThread = !ThreadUtils.isRunGameThread();
        if (renameThread) {
            thread.setName(ThreadUtils.THREAD_PREFIX_GAME + " benchmark fixture");
        }
        try {
            return createInGameThread();
        } finally {
            if (renameThread) {
                thread.setName(originalThreadName);
            }
        }
    }

    private static Snapshot createInGameThread() throws Exception {
        init();
        DeterministicGameFixture fixture = new DeterministicGameFixture();
        fixture.reset();
        fixture.gameOptions.skipInitShuffling = true;
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
        Snapshot snapshot = new Snapshot(game, gameView);
        validate(snapshot);
        return snapshot;
    }

    private static void validate(Snapshot snapshot) {
        if (snapshot == null
                || snapshot.game == null
                || snapshot.gameView == null
                || snapshot.game.getPlayers().size() != 2
                || snapshot.game.getBattlefield().getAllPermanents().size() != 18
                || snapshot.gameView.getPlayers().size() != 2) {
            throw new IllegalStateException("Deterministic fixture shape changed");
        }
        int visiblePermanents = 0;
        for (PlayerView player : snapshot.gameView.getPlayers()) {
            visiblePermanents += player.getBattlefield().size();
        }
        if (visiblePermanents != 18) {
            throw new IllegalStateException("Deterministic fixture view shape changed");
        }
        String actualFingerprint = snapshot.calculateSemanticFingerprint();
        if (snapshot.expectedSemanticFingerprint == null
                || !snapshot.expectedSemanticFingerprint.equals(actualFingerprint)) {
            throw new IllegalStateException("Deterministic fixture fingerprint changed");
        }
    }

    public static final class Snapshot implements Serializable {
        private static final long serialVersionUID = 1L;

        private final Game game;
        private final GameView gameView;
        private final String expectedSemanticFingerprint;

        private Snapshot(Game game, GameView gameView) {
            this.game = game;
            this.gameView = gameView;
            this.expectedSemanticFingerprint = calculateSemanticFingerprint();
        }

        public Game getGame() {
            return game;
        }

        public GameView getGameView() {
            return gameView;
        }

        public String semanticFingerprint() {
            return calculateSemanticFingerprint();
        }

        private String calculateSemanticFingerprint() {
            List<String> gamePlayers = new ArrayList<>();
            for (Player player : game.getPlayers().values()) {
                gamePlayers.add(player.getName()
                        + ":life=" + player.getLife()
                        + ":library=" + player.getLibrary().size()
                        + ":hand=" + player.getHand().size());
            }
            Collections.sort(gamePlayers);

            List<String> gamePermanents = new ArrayList<>();
            for (Permanent permanent : game.getBattlefield().getAllPermanents()) {
                Player controller = game.getPlayer(permanent.getControllerId());
                gamePermanents.add(permanent.getName()
                        + ":controller=" + (controller == null ? "missing" : controller.getName()));
            }
            Collections.sort(gamePermanents);

            List<String> viewPlayers = new ArrayList<>();
            for (PlayerView player : gameView.getPlayers()) {
                List<String> permanents = new ArrayList<>();
                for (CardView permanent : player.getBattlefield().values()) {
                    permanents.add(permanent.getName());
                }
                Collections.sort(permanents);
                viewPlayers.add(player.getName()
                        + ":life=" + player.getLife()
                        + ":library=" + player.getLibraryCount()
                        + ":hand=" + player.getHandCount()
                        + ":battlefield=" + permanents);
            }
            Collections.sort(viewPlayers);
            String description = "game:turn=" + game.getTurnNum()
                    + ":phase=" + (game.getPhase() == null ? "null" : game.getPhase().getType())
                    + ":step=" + (game.getStep() == null ? "null" : game.getStep().getType())
                    + ":players=" + gamePlayers
                    + ":battlefield=" + gamePermanents
                    + "|view:turn=" + gameView.getTurn()
                    + ":phase=" + gameView.getPhase()
                    + ":step=" + gameView.getStep()
                    + ":players=" + viewPlayers;
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hash = digest.digest(description.getBytes(StandardCharsets.UTF_8));
                StringBuilder result = new StringBuilder(hash.length * 2);
                for (byte value : hash) {
                    result.append(String.format("%02x", value & 0xff));
                }
                return result.toString();
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("SHA-256 is unavailable", e);
            }
        }
    }
}
