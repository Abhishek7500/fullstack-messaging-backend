package com.messenger.web;

import com.messenger.dto.SubscriptionStatusDto;
import com.messenger.security.SecurityUtils;
import com.messenger.service.SubscriptionStatusService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Tier status + mock upgrade workflow for the premium prompt modal. */
@RestController
@RequestMapping("/api/subscriptions")
public class SubscriptionController {

    private final SubscriptionStatusService subscriptionStatusService;

    public SubscriptionController(SubscriptionStatusService subscriptionStatusService) {
        this.subscriptionStatusService = subscriptionStatusService;
    }

    @GetMapping("/me")
    public SubscriptionStatusDto me() {
        return subscriptionStatusService.status(SecurityUtils.currentUser());
    }

    @PostMapping("/upgrade")
    public SubscriptionStatusDto upgrade() {
        return subscriptionStatusService.upgrade(SecurityUtils.currentUser());
    }
}
