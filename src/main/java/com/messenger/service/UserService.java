package com.messenger.service;

import com.messenger.domain.UserAccount;
import com.messenger.dto.UserDto;
import com.messenger.repo.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService {

    private final UserAccountRepository userAccountRepository;
    private final PresenceService presenceService;

    public UserService(UserAccountRepository userAccountRepository, PresenceService presenceService) {
        this.userAccountRepository = userAccountRepository;
        this.presenceService = presenceService;
    }

    @Transactional(readOnly = true)
    public UserDto profile(UserAccount current) {
        return UserDto.from(current, presenceService.isOnline(current.getId()));
    }

    @Transactional(readOnly = true)
    public UserDto getById(Long id) {
        UserAccount user = userAccountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return UserDto.from(user, presenceService.isOnline(user.getId()));
    }

    @Transactional(readOnly = true)
    public List<UserDto> search(UserAccount current, String query) {
        List<UserAccount> users = (query == null || query.isBlank())
                ? userAccountRepository.findByIdNotOrderByDisplayNameAsc(current.getId())
                : userAccountRepository.searchUsers(current.getId(), query.trim());
        return users.stream()
                .map(user -> UserDto.from(user, presenceService.isOnline(user.getId())))
                .toList();
    }
}
