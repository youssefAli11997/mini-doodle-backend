package com.example.doodle.meeting.api;

import com.example.doodle.slot.application.SlotDto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MeetingResponse(
        UUID id,
        UUID slotId,
        String title,
        String description,
        Instant createdAt,
        Instant updatedAt,
        List<UUID> participantIds,
        SlotDto slot
) {
}
