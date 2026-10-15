package com.messenger.service;

import com.messenger.dto.ChatEvents.GameMovePayload;
import com.messenger.dto.ChatEvents.GameStateEvent;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GameService {

    private static final List<int[]> LINES = List.of(
            new int[]{0, 1, 2}, new int[]{3, 4, 5}, new int[]{6, 7, 8},
            new int[]{0, 3, 6}, new int[]{1, 4, 7}, new int[]{2, 5, 8},
            new int[]{0, 4, 8}, new int[]{2, 4, 6}
    );

    private final SimpMessagingTemplate messagingTemplate;
    private final Map<Long, GameSession> games = new ConcurrentHashMap<>();

    public GameService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void applyMove(GameMovePayload move, Long playerId) {
        GameSession game = games.computeIfAbsent(move.conversationId(), ignored -> new GameSession());
        GameStateEvent event;
        synchronized (game) {
            if (move.row() == -1 && move.column() == -1) {
                Arrays.fill(game.board, null);
                game.playerXId = null;
                game.playerOId = null;
                event = toEvent(move.conversationId(), game, playerId);
            } else {
                if (move.row() < 0 || move.row() > 2 || move.column() < 0 || move.column() > 2) {
                    throw new IllegalArgumentException("Game move coordinates must be between 0 and 2");
                }

                String winner = winnerOf(game.board);
                if (winner != null || isFull(game.board)) {
                    throw new IllegalArgumentException("The game has already ended");
                }
                int index = move.row() * 3 + move.column();
                if (game.board[index] != null) {
                    throw new IllegalArgumentException("That game cell is already occupied");
                }

                boolean xIsNext = occupiedCount(game.board) % 2 == 0;
                String mark;
                if (game.playerXId == null) {
                    if (!xIsNext) {
                        throw new IllegalStateException("The game state is invalid");
                    }
                    game.playerXId = playerId;
                    mark = "X";
                } else if (game.playerXId.equals(playerId)) {
                    if (!xIsNext) {
                        throw new IllegalArgumentException("It is the other player's turn");
                    }
                    mark = "X";
                } else {
                    if (game.playerOId == null) {
                        if (xIsNext) {
                            throw new IllegalArgumentException("The first player must make the opening move");
                        }
                        game.playerOId = playerId;
                    }
                    if (!game.playerOId.equals(playerId) || xIsNext) {
                        throw new IllegalArgumentException("It is not this player's turn");
                    }
                    mark = "O";
                }
                game.board[index] = mark;
                event = toEvent(move.conversationId(), game, playerId);
            }
        }
        messagingTemplate.convertAndSend("/topic/conversations/" + move.conversationId() + "/game", event);
    }

    private GameStateEvent toEvent(Long conversationId, GameSession game, Long lastMoveBy) {
        String winner = winnerOf(game.board);
        boolean full = isFull(game.board);
        String resolvedWinner = winner != null ? winner : full ? "DRAW" : null;
        List<List<String>> matrix = new ArrayList<>(3);
        for (int row = 0; row < 3; row++) {
            matrix.add(Arrays.asList(
                    game.board[row * 3],
                    game.board[row * 3 + 1],
                    game.board[row * 3 + 2]
            ));
        }
        return new GameStateEvent(
                conversationId,
                matrix,
                winner == null && !full && occupiedCount(game.board) % 2 == 0,
                resolvedWinner,
                winLineOf(game.board),
                lastMoveBy,
                game.playerXId,
                game.playerOId,
                Instant.now()
        );
    }

    private String winnerOf(String[] board) {
        for (int[] line : LINES) {
            String mark = board[line[0]];
            if (mark != null && mark.equals(board[line[1]]) && mark.equals(board[line[2]])) {
                return mark;
            }
        }
        return null;
    }

    private List<Integer> winLineOf(String[] board) {
        for (int[] line : LINES) {
            String mark = board[line[0]];
            if (mark != null && mark.equals(board[line[1]]) && mark.equals(board[line[2]])) {
                return List.of(line[0], line[1], line[2]);
            }
        }
        return List.of();
    }

    private boolean isFull(String[] board) {
        return occupiedCount(board) == board.length;
    }

    private int occupiedCount(String[] board) {
        int count = 0;
        for (String cell : board) {
            if (cell != null) count++;
        }
        return count;
    }

    private static final class GameSession {
        private final String[] board = new String[9];
        private Long playerXId;
        private Long playerOId;
    }
}
