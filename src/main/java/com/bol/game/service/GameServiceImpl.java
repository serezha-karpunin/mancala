package com.bol.game.service;

import com.bol.exception.ApplicationException;
import com.bol.game.Game;
import com.bol.game.dto.request.CreateGameDto;
import com.bol.game.dto.request.RequestTurnDto;
import com.bol.game.engine.GameEngine;
import com.bol.game.engine.exception.GameEngineException;
import com.bol.game.engine.model.GameState;
import com.bol.game.engine.model.GameStatus;
import com.bol.game.engine.model.Player;
import com.bol.game.repository.GameRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class GameServiceImpl implements GameService {
    private final GameEngine gameEngine;
    private final GameRepository gameRepository;

    public GameServiceImpl(GameEngine gameEngine, GameRepository gameRepository) {
        this.gameEngine = gameEngine;
        this.gameRepository = gameRepository;
    }

    @Override
    @Transactional
    public Game createGame(UUID userId, CreateGameDto body) {
        var state = gameEngine.createGame(
                body.pitsPerPlayer(),
                body.stonesPerSpace(),
                body.isStealingAllowed(),
                body.isMultipleTurnAllowed()
        );
        gameEngine.addPlayer(userId, state);

        return gameRepository.save(new Game(state));
    }

    @Override
    @Transactional
    public Game joinGame(UUID userId, UUID gameId) {
        var game = lockGameById(gameId);
        var state = game.getState();
        var status = state.getStatus();
        if (status != GameStatus.WAITING_FOR_PLAYERS) {
            throw ApplicationException.badRequest("Game is not in waiting state: gameId=%s, gameStatus=%s".formatted(gameId, status));
        }

        var isAlreadyJoined = state.getPlayers().stream()
                .map(Player::userId)
                .anyMatch(userId::equals);
        if (isAlreadyJoined) {
            throw ApplicationException.badRequest("User is already joined: gameId=%s, userId=%s".formatted(gameId, userId));
        }

        gameEngine.addPlayer(userId, state);
        gameEngine.initialize(state);

        return gameRepository.save(game);
    }

    private Game lockGameById(UUID gameId) {
        return gameRepository.lockById(gameId)
                .orElseThrow(() -> ApplicationException.badRequest("Game is not found: gameId=%s".formatted(gameId)));
    }

    @Override
    @Transactional
    public Game requestTurn(UUID gameId, RequestTurnDto body) {
        var game = lockGameById(gameId);
        var state = game.getState();
        var status = state.getStatus();
        if (status != GameStatus.ACTIVE) {
            throw ApplicationException.badRequest("Game is not in active state: gameId=%s, gameStatus=%s".formatted(gameId, status));
        }

        var userId = body.userId();
        var playerIndex = getPlayerIndex(userId, state)
                .orElseThrow(() -> ApplicationException.badRequest("User is not a player: gameId=%s, userId=%s".formatted(gameId, userId)));

        wrapGameEngineException(
                gameId, () -> gameEngine.turn(playerIndex, body.spaceIndex(), state)
        );

        return gameRepository.save(game);
    }

    private static Optional<Integer> getPlayerIndex(UUID userId, GameState game) {
        // TODO: Refactor, consider moving playerIndex to Player structure
        var players = game.getPlayers();
        for (int i = 0; i < players.size(); i++) {
            var player = players.get(i);
            if (player.userId().equals(userId)) {
                return Optional.of(i);
            }
        }
        return Optional.empty();
    }

    private void wrapGameEngineException(UUID gameId, Runnable function) {
        try {
            function.run();
        } catch (GameEngineException exception) {
            var msg = "Request turn for gameId=%s failed. Engine message: %s"
                    .formatted(gameId, exception.getMessage());
            throw ApplicationException.badRequest(msg);
        }
    }
}
