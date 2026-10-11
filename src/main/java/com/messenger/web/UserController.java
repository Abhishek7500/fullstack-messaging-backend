package com.messenger.web;

import com.messenger.dto.UserDto;
import com.messenger.security.SecurityUtils;
import com.messenger.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public UserDto me() {
        return userService.profile(SecurityUtils.currentUser());
    }

    @GetMapping("/{id}")
    public UserDto byId(@PathVariable Long id) {
        return userService.getById(id);
    }

    @GetMapping
    public List<UserDto> search(@RequestParam(required = false) String q) {
        return userService.search(SecurityUtils.currentUser(), q);
    }
}
