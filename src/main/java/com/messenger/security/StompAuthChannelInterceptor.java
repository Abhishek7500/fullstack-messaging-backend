package com.messenger.security;

import com.messenger.domain.UserAccount;
import com.messenger.repo.UserAccountRepository;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;
    private final UserAccountRepository userAccountRepository;

    public StompAuthChannelInterceptor(JwtService jwtService, UserAccountRepository userAccountRepository) {
        this.jwtService = jwtService;
        this.userAccountRepository = userAccountRepository;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = resolveToken(accessor);
            if (token == null) {
                throw new IllegalArgumentException("Missing JWT for WebSocket CONNECT");
            }
            String username = jwtService.extractUsername(token);
            UserAccount user = userAccountRepository.findByUsernameIgnoreCase(username)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown user"));
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    user,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_USER"))
            );
            accessor.setUser(auth);
            if (accessor.getSessionAttributes() != null) {
                accessor.getSessionAttributes().put("userId", user.getId());
                accessor.getSessionAttributes().put("username", user.getUsername());
            }
        }
        return message;
    }

    private String resolveToken(StompHeaderAccessor accessor) {
        String auth = accessor.getFirstNativeHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring(7);
        }
        String token = accessor.getFirstNativeHeader("token");
        if (token != null && !token.isBlank()) {
            return token;
        }
        return null;
    }
}
