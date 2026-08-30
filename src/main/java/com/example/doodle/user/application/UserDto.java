package com.example.doodle.user.application;

import java.time.Instant;
import java.util.UUID;

public record UserDto(
        UUID id,
        String name,
        String email,
        Instant createdAt,
        Instant updatedAt
) {
}
