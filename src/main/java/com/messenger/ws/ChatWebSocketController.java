package com.messenger.ws;

import com.messenger.domain.ChatMessage;
import com.messenger.domain.UserAccount;
import com.messenger.dto.ChatEvents.GameMovePayload;
import com.messenger.dto.ChatEvents.ReactionRequest;
import com.messenger.dto.ChatEvents.SendMessagePayload;
import com.messenger.dto.ChatEvents.TypingEvent;
import com.messenger.dto.ChatEvents.TypingPayload;
import com.messenger.dto.MessageDto;
import com.messenger.security.SecurityUtils;
import com.messenger.service.ConversationService;
import com.messenger.service.GameService;
import com.messenger.service.ReactionService;
import jakarta.validation.Valid;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class ChatWebSocketController {

    private final ConversationService conversationService;
    private final ReactionService reactionService;
    private final GameService gameService;
    private final SimpMessagingTemplate messagingTemplate;

    public ChatWebSocketController(
            ConversationService conversationService,
            ReactionService reactionService,
            GameService gameService,
            SimpMessagingTemplate messagingTemplate
    ) {
        this.conversationService = conversationService;
        this.reactionService = reactionService;
        this.gameService = gameService;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/chat.send")
    public void send(@Valid @Payload SendMessagePayload payload, Principal principal) {
        UserAccount sender = SecurityUtils.fromPrincipal(principal);
        ChatMessage saved = conversationService.saveMessage(
                sender,
                payload.conversationId(),
                payload.content(),
                payload.messageType(),
                payload.mediaUrl(),
                payload.unlockAt()
        );
        // Sealed capsules are broadcast in their redacted form so no client can
        // peek at the content before unlock time.
        MessageDto dto = MessageDto.from(saved);
        messagingTemplate.convertAndSend("/topic/conversations/" + payload.conversationId(), dto);
    }

    @MessageMapping("/chat.typing")
    public void typing(@Valid @Payload TypingPayload payload, Principal principal) {
        UserAccount sender = SecurityUtils.fromPrincipal(principal);
        conversationService.requireParticipant(payload.conversationId(), sender.getId());
        TypingEvent event = new TypingEvent(
                payload.conversationId(),
                sender.getId(),
                sender.getDisplayName(),
                payload.typing()
        );
        messagingTemplate.convertAndSend("/topic/conversations/" + payload.conversationId() + "/typing", event);
    }

    @MessageMapping("/chat.react")
    public void react(@Valid @Payload ReactionRequest payload, Principal principal) {
        UserAccount sender = SecurityUtils.fromPrincipal(principal);
        reactionService.toggle(sender, payload.messageId(), payload.reactionType());
    }

    /**
     * Live multiplayer Tic-Tac-Toe move. The server validates participation,
     * applies the move to the authoritative room board, and broadcasts the
     * updated matrix to every subscriber of
     * {@code /topic/conversations/{id}/game}.
     */
    @MessageMapping("/chat.gamemove")
    public void gameMove(@Valid @Payload GameMovePayload payload, Principal principal) {
        UserAccount sender = SecurityUtils.fromPrincipal(principal);
        conversationService.requireParticipant(payload.conversationId(), sender.getId());
        gameService.applyMove(payload, sender.getId());
    }
}
