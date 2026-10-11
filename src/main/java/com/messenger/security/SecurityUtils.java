package com.messenger.security;

import com.messenger.domain.UserAccount;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.security.Principal;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static UserAccount currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserAccount user) {
            return user;
        }
        throw new SecurityException("Authentication required");
    }

    public static UserAccount fromPrincipal(Principal principal) {
        if (principal instanceof UsernamePasswordAuthenticationToken token
                && token.getPrincipal() instanceof UserAccount user) {
            return user;
        }
        throw new SecurityException("Authentication required");
    }
}
