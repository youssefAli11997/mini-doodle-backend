package com.example.doodle.meeting.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record Meeting(
        UUID id,
        UUID slotId,
        String title,
        String description,
        Instant createdAt,
        Instant updatedAt,
        List<UUID> participantIds
) {
    public static Meeting create(UUID slotId, String title, String description, List<UUID> participantIds) {
        if (slotId == null) {
            throw new IllegalArgumentException("Slot ID is required");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Meeting title is required");
        }
        if (participantIds == null || participantIds.size() < 2) {
            throw new IllegalArgumentException("A meeting must have at least 2 participants");
        }
        Instant now = Instant.now();
        return new Meeting(UUID.randomUUID(), slotId, title, description, now, now, List.copyOf(participantIds));
    }
}
