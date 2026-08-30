package com.example.doodle.user.application;

import com.example.doodle.common.exception.ConflictException;
import com.example.doodle.common.exception.ResourceNotFoundException;
import com.example.doodle.user.api.CreateUserRequest;
import com.example.doodle.user.domain.User;
import com.example.doodle.user.persistence.UserEntity;
import com.example.doodle.user.persistence.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public User createUser(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("EMAIL_ALREADY_EXISTS", "A user with email " + request.email() + " already exists.");
        }

        User user = User.create(request.name(), request.email());
        UserEntity entity = UserMapper.toEntity(user);
        UserEntity saved = userRepository.save(entity);
        return UserMapper.toDomain(saved);
    }

    @Transactional(readOnly = true)
    public User getUser(UUID userId) {
        return userRepository.findById(userId)
                .map(UserMapper::toDomain)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "User with ID " + userId + " was not found."));
    }
}
