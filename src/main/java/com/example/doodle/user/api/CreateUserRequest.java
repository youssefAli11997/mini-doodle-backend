package com.example.doodle.user.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CreateUserRequest(
        @NotBlank(message = "User name is required")
        String name,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        String email
) {
}
