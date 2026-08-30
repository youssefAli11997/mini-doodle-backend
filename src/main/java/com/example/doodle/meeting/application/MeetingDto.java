package com.example.doodle.meeting.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MeetingDto(
        UUID id,
        UUID slotId,
        String title,
        String description,
        Instant createdAt,
        Instant updatedAt,
        List<UUID> participantIds
) {
}
