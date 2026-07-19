package mage.server.game;

import mage.constants.Zone;
import mage.game.Game;
import mage.players.Player;
import mage.util.ThreadUtils;
import mage.view.GameView;
import mage.view.SimpleCardsView;

import java.util.UUID;

final class GameViewBuilder {

    private GameViewBuilder() {
    }

    static GameView fromStableGameForPlayer(Game game, UUID playerId, UUID userId) {
        ThreadUtils.ensureRunInGameThread();
        return renderPlayerView(game, playerId, userId);
    }

    static GameView fromDefensiveCopyForPlayer(Game game, UUID playerId, UUID userId) {
        return renderPlayerView(game.copy(), playerId, userId);
    }

    static GameView fromStableGameForWatcher(Game game, UUID userId) {
        ThreadUtils.ensureRunInGameThread();
        return renderWatcherView(game, userId);
    }

    static GameView fromDefensiveCopyForWatcher(Game game, UUID userId) {
        return renderWatcherView(game.copy(), userId);
    }

    static GameView renderPlayerView(Game game, UUID playerId, UUID userId) {
        GameView gameView = new GameView(game.getState(), game, playerId, null);

        // playable info (if opponent under control then show opponent's playable)
        Player player = game.getPlayer(playerId); // null for watcher
        Player priorityPlayer = game.getPlayer(game.getPriorityPlayerId());
        Player controllingPlayer = priorityPlayer == null ? null : game.getPlayer(priorityPlayer.getTurnControlledBy());
        if (controllingPlayer != null && player == controllingPlayer) {
            gameView.setCanPlayObjects(priorityPlayer.getPlayableObjectsReadOnly(game, Zone.ALL));
        }

        processControlledPlayers(game, player, gameView);
        processWatchedHands(game, userId, gameView);
        //TODO: should player who controls another player's turn be able to look at all these cards?

        return gameView;
    }

    static GameView renderWatcherView(Game game, UUID userId) {
        GameView gameView = new GameView(game.getState(), game, null, userId);
        processWatchedHands(game, userId, gameView);
        return gameView;
    }

    private static void processControlledPlayers(Game game, Player player, GameView gameView) {
        if (player == null) {
            // ignore watcher
            return;
        }
        gameView.getOpponentHands().clear();
        if (!player.getPlayersUnderYourControl().isEmpty()) {
            for (UUID controlledPlayerId : player.getPlayersUnderYourControl()) {
                Player opponent = game.getPlayer(controlledPlayerId);
                gameView.getOpponentHands().put(opponent.getName(), new SimpleCardsView(opponent.getHand().getCards(game), true));
            }
        }
    }

    private static void processWatchedHands(Game game, UUID userId, GameView gameView) {
        gameView.getWatchedHands().clear();
        for (Player player : game.getPlayers().values()) {
            if (player.hasUserPermissionToSeeHand(userId)) {
                gameView.getWatchedHands().put(player.getName(), new SimpleCardsView(player.getHand().getCards(game), true));
            }
        }
    }
}
