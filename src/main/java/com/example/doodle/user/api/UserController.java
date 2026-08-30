package com.example.doodle.user.api;

import com.example.doodle.user.application.UserDto;
import com.example.doodle.user.application.UserMapper;
import com.example.doodle.user.application.UserService;
import com.example.doodle.user.domain.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserDto createUser(@Valid @RequestBody CreateUserRequest request) {
        User user = userService.createUser(request);
        return UserMapper.toDto(user);
    }

    @GetMapping("/{userId}")
    public UserDto getUser(@PathVariable("userId") UUID userId) {
        User user = userService.getUser(userId);
        return UserMapper.toDto(user);
    }
}
