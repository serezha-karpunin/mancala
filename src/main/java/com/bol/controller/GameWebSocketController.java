package com.bol.controller;

import com.bol.dto.request.RequestTurnDto;
import com.bol.service.GameService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.util.UUID;

@Controller
public class GameWebSocketController {

    private final GameService gameService;

    public GameWebSocketController(GameService gameService) {
        this.gameService = gameService;
    }

    @MessageMapping("/game.{gameId}")
    public void requestTurn(@DestinationVariable UUID gameId, @Payload RequestTurnDto payload) {
        gameService.requestTurn(gameId, payload);
    }
}
