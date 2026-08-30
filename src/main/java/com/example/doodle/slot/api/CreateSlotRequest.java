package com.example.doodle.slot.api;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record CreateSlotRequest(
        @NotNull(message = "Start time is required")
        Instant startTime,

        @NotNull(message = "End time is required")
        Instant endTime
) {
}
