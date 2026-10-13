package com.messenger.web;

import com.messenger.dto.ChatEvents.ReactionToggleRequest;
import com.messenger.security.SecurityUtils;
import com.messenger.service.ReactionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST entry point for toggling reactions. The toggle result is broadcast to
 * the conversation topic by {@link ReactionService}; this endpoint returns the
 * updated message id so the caller can reconcile optimistic UI state.
 */
@RestController
@RequestMapping("/api/messages")
public class ReactionController {

    private final ReactionService reactionService;

    public ReactionController(ReactionService reactionService) {
        this.reactionService = reactionService;
    }

    @PostMapping("/{id}/reactions")
    @ResponseStatus(HttpStatus.OK)
    public Map<String, Object> toggle(
            @PathVariable("id") Long messageId,
            @Valid @RequestBody ReactionToggleRequest request
    ) {
        Long updated = reactionService.toggle(SecurityUtils.currentUser(), messageId, request.reactionType());
        return Map.of("messageId", updated, "reactionType", request.reactionType());
    }
}
