package com.messenger.web;

import com.messenger.dto.ChatEvents.CreateConversationRequest;
import com.messenger.dto.ConversationDto;
import com.messenger.dto.MessageDto;
import com.messenger.security.SecurityUtils;
import com.messenger.service.ConversationService;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @GetMapping
    public List<ConversationDto> list() {
        return conversationService.listFor(SecurityUtils.currentUser());
    }

    @GetMapping("/{id}")
    public ConversationDto get(@PathVariable Long id) {
        return conversationService.getFor(SecurityUtils.currentUser(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ConversationDto create(@RequestBody CreateConversationRequest request) {
        return conversationService.createOrGet(SecurityUtils.currentUser(), request);
    }

    @GetMapping("/{id}/messages")
    public Page<MessageDto> messages(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "40") int size
    ) {
        return conversationService.history(SecurityUtils.currentUser(), id, page, size);
    }

    @PutMapping("/{id}/read")
    public ConversationDto markRead(@PathVariable Long id) {
        return conversationService.markRead(SecurityUtils.currentUser(), id);
    }
}
