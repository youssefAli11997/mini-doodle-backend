package com.example.doodle.meeting.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record BookMeetingRequest(
        @NotBlank(message = "Meeting title is required")
        String title,

        String description,

        @NotEmpty(message = "Participant IDs list cannot be empty")
        List<UUID> participantIds
) {
}
