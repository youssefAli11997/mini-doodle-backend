package com.example.doodle.user.domain;

import java.time.Instant;
import java.util.UUID;

public record User(
        UUID id,
        String name,
        String email,
        Instant createdAt,
        Instant updatedAt
) {
    public static User create(String name, String email) {
        Instant now = Instant.now();
        return new User(UUID.randomUUID(), name, email, now, now);
    }
}
