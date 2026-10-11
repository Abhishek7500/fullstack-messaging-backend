package com.messenger.service;

import com.messenger.domain.UserAccount;
import com.messenger.dto.AuthDtos.AuthResponse;
import com.messenger.dto.AuthDtos.LoginRequest;
import com.messenger.dto.AuthDtos.RegisterRequest;
import com.messenger.dto.UserDto;
import com.messenger.repo.UserAccountRepository;
import com.messenger.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final String[] AVATAR_COLORS = {
            "#0084FF", "#00A400", "#F5A623", "#D0021B", "#7B61FF", "#E91E63", "#00BCD4"
    };

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final PresenceService presenceService;

    public AuthService(
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            PresenceService presenceService
    ) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.presenceService = presenceService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userAccountRepository.existsByUsernameIgnoreCase(request.username())) {
            throw new IllegalStateException("Username is already taken");
        }
        if (userAccountRepository.existsByEmailIgnoreCase(request.email())) {
            throw new IllegalStateException("Email is already registered");
        }
        UserAccount user = new UserAccount();
        user.setUsername(request.username().trim());
        user.setEmail(request.email().trim().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName(request.displayName().trim());
        user.setAvatarColor(pickColor(request.username()));
        UserAccount saved = userAccountRepository.save(user);
        return toAuthResponse(saved);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        UserAccount user = userAccountRepository.findByUsernameIgnoreCase(request.username())
                .orElseThrow(() -> new IllegalArgumentException("Invalid username or password"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid username or password");
        }
        return toAuthResponse(user);
    }

    public AuthResponse toAuthResponse(UserAccount user) {
        String token = jwtService.generateToken(user.getId(), user.getUsername());
        return new AuthResponse(token, UserDto.from(user, presenceService.isOnline(user.getId())));
    }

    private String pickColor(String seed) {
        int index = Math.abs(seed.hashCode()) % AVATAR_COLORS.length;
        return AVATAR_COLORS[index];
    }
}
