package com.messenger.service;

import com.messenger.dto.ChatEvents.GameMovePayload;
import com.messenger.dto.ChatEvents.GameStateEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import org.mockito.ArgumentCaptor;

class GameServiceTest {

    private static final Long CONVERSATION_ID = 42L;
    private static final Long PLAYER_X = 11L;
    private static final Long PLAYER_O = 22L;

    private SimpMessagingTemplate messagingTemplate;
    private GameService gameService;

    @BeforeEach
    void setUp() {
        messagingTemplate = mock(SimpMessagingTemplate.class);
        gameService = new GameService(messagingTemplate);
    }

    @Test
    void broadcastsMatrixAndAssignsAlternatingPlayers() {
        gameService.applyMove(new GameMovePayload(CONVERSATION_ID, 1, 2), PLAYER_X);

        ArgumentCaptor<GameStateEvent> firstState = ArgumentCaptor.forClass(GameStateEvent.class);
        verify(messagingTemplate).convertAndSend(
                eq("/topic/conversations/" + CONVERSATION_ID + "/game"),
                firstState.capture()
        );
        assertEquals("X", firstState.getValue().board().get(1).get(2));
        assertEquals(PLAYER_X, firstState.getValue().playerXId());
        assertNull(firstState.getValue().playerOId());
        assertFalse(firstState.getValue().xIsNext());

        gameService.applyMove(new GameMovePayload(CONVERSATION_ID, 0, 0), PLAYER_O);
        ArgumentCaptor<GameStateEvent> secondState = ArgumentCaptor.forClass(GameStateEvent.class);
        verify(messagingTemplate, times(2)).convertAndSend(
                eq("/topic/conversations/" + CONVERSATION_ID + "/game"),
                secondState.capture()
        );
        assertEquals("O", secondState.getValue().board().get(0).get(0));
        assertEquals(PLAYER_O, secondState.getValue().playerOId());
        assertTrue(secondState.getValue().xIsNext());
        assertThrows(
                IllegalArgumentException.class,
                () -> gameService.applyMove(new GameMovePayload(CONVERSATION_ID, 2, 2), PLAYER_O)
        );
    }

    @Test
    void resetClearsTheBoardAndPlayerAssignments() {
        gameService.applyMove(new GameMovePayload(CONVERSATION_ID, 0, 0), PLAYER_X);
        gameService.applyMove(new GameMovePayload(CONVERSATION_ID, -1, -1), PLAYER_O);

        ArgumentCaptor<GameStateEvent> state = ArgumentCaptor.forClass(GameStateEvent.class);
        verify(messagingTemplate, times(2)).convertAndSend(
                eq("/topic/conversations/" + CONVERSATION_ID + "/game"),
                state.capture()
        );
        GameStateEvent reset = state.getAllValues().get(1);
        assertTrue(reset.board().stream().flatMap(java.util.List::stream).allMatch(cell -> cell == null));
        assertNull(reset.playerXId());
        assertNull(reset.playerOId());
        assertTrue(reset.xIsNext());
    }
}
