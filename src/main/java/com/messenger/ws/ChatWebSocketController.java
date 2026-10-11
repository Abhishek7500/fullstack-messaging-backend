package com.messenger.ws;

import com.messenger.domain.ChatMessage;
import com.messenger.domain.UserAccount;
import com.messenger.dto.ChatEvents.SendMessagePayload;
import com.messenger.dto.ChatEvents.TypingEvent;
import com.messenger.dto.ChatEvents.TypingPayload;
import com.messenger.dto.MessageDto;
import com.messenger.security.SecurityUtils;
import com.messenger.service.ConversationService;
import jakarta.validation.Valid;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class ChatWebSocketController {

    private final ConversationService conversationService;
    private final SimpMessagingTemplate messagingTemplate;

    public ChatWebSocketController(
            ConversationService conversationService,
            SimpMessagingTemplate messagingTemplate
    ) {
        this.conversationService = conversationService;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/chat.send")
    public void send(@Valid @Payload SendMessagePayload payload, Principal principal) {
        UserAccount sender = SecurityUtils.fromPrincipal(principal);
        ChatMessage saved = conversationService.saveMessage(sender, payload.conversationId(), payload.content());
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
}
