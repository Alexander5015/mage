package mage.server.game;

import mage.game.Game;
import mage.game.Table;
import mage.interfaces.callback.ClientCallback;
import mage.interfaces.callback.ClientCallbackMethod;
import mage.server.User;
import mage.server.managers.UserManager;
import mage.view.GameClientMessage;
import mage.view.GameEndView;
import mage.view.GameView;
import org.apache.log4j.Logger;

import java.util.Optional;
import java.util.UUID;

/**
 * @author BetaSteward_at_googlemail.com
 */
public class GameSessionWatcher {

    protected static final Logger logger = Logger.getLogger(GameSessionWatcher.class);

    private final UserManager userManager;
    protected final UUID userId;
    protected final Game game;
    protected boolean killed = false;
    protected final boolean isPlayer;

    public GameSessionWatcher(UserManager userManager, UUID userId, Game game, boolean isPlayer) {
        this.userManager = userManager;
        this.userId = userId;
        this.game = game;
        this.isPlayer = isPlayer;
    }

    public boolean init() {
        if (!killed) {
            Optional<User> user = userManager.getUser(userId);
            if (user.isPresent()) {
                // TODO: can be called outside of the game thread, e.g. user start watching already running game
                //    possible fix: getGameView must use last cached value in non game thread call (split by sessions)
                user.get().fireCallback(new ClientCallback(ClientCallbackMethod.GAME_INIT, game.getId(), getGameView()));
                return true;
            }
        }
        return false;
    }

    public void update() {
        if (!killed) {
            userManager.getUser(userId).ifPresent(user -> user.fireCallback(new ClientCallback(ClientCallbackMethod.GAME_UPDATE, game.getId(), getGameViewFromStableGame())));
        }

    }

    public void inform(final String message) {
        if (!killed) {
            userManager.getUser(userId).ifPresent(user -> user.fireCallback(new ClientCallback(ClientCallbackMethod.GAME_UPDATE_AND_INFORM, game.getId(), new GameClientMessage(getGameViewFromStableGame(), null, message))));
        }

    }

    public void informPersonal(final String message) {
        if (!killed) {
            userManager.getUser(userId).ifPresent(user -> user.fireCallback(new ClientCallback(ClientCallbackMethod.GAME_INFORM_PERSONAL, game.getId(), new GameClientMessage(getGameViewFromStableGame(), null, message))));
        }

    }

    public void gameOver(final String message) {
        if (!killed) {
            userManager.getUser(userId).ifPresent(user -> {
                user.removeGameWatchInfo(game.getId());
                user.fireCallback(new ClientCallback(ClientCallbackMethod.GAME_OVER, game.getId(), new GameClientMessage(getGameViewFromStableGame(), null, message)));
            });
        }
    }

    /**
     * Cleanup if Session ends
     */
    public void cleanUp() {

    }

    public void gameError(final String message) {
        if (!killed) {
            // TODO: implement shared link here (store game state, full battlefield info, history/logs, etc with shared link to web page)
            userManager.getUser(userId).ifPresent(user -> user.fireCallback(new ClientCallback(ClientCallbackMethod.GAME_ERROR, game.getId(), message)));
        }
    }

    public void setKilled() {
        killed = true;
    }

    public GameView getGameView() {
        return GameViewBuilder.fromDefensiveCopyForWatcher(game, userId);
    }

    GameView getGameViewFromStableGame() {
        return GameViewBuilder.fromStableGameForWatcher(game, userId);
    }

    public GameEndView getGameEndView(UUID playerId, Table table) {
        return new GameEndView(game.getState(), game, playerId, table);
    }

    public boolean isPlayer() {
        return isPlayer;
    }

}
