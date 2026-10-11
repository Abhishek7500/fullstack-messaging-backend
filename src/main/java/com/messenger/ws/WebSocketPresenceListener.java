package com.messenger.ws;

import com.messenger.domain.UserAccount;
import com.messenger.security.SecurityUtils;
import com.messenger.service.PresenceService;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
public class WebSocketPresenceListener {

    private final PresenceService presenceService;

    public WebSocketPresenceListener(PresenceService presenceService) {
        this.presenceService = presenceService;
    }

    @EventListener
    public void onConnect(SessionConnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        if (accessor.getUser() == null || accessor.getSessionId() == null) {
            return;
        }
        UserAccount user = SecurityUtils.fromPrincipal(accessor.getUser());
        presenceService.userConnected(user, accessor.getSessionId());
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        if (event.getSessionId() != null) {
            presenceService.userDisconnected(event.getSessionId());
        }
    }
}
