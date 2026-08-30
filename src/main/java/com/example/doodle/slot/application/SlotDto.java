package com.example.doodle.slot.application;

import com.example.doodle.slot.domain.SlotStatus;

import java.time.Instant;
import java.util.UUID;

public record SlotDto(
        UUID id,
        UUID userId,
        Instant startTime,
        Instant endTime,
        SlotStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
