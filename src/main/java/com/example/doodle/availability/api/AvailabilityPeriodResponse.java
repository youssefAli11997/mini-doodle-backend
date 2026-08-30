package com.example.doodle.availability.api;

import com.example.doodle.slot.domain.SlotStatus;

import java.time.Instant;

public record AvailabilityPeriodResponse(
        Instant startTime,
        Instant endTime,
        SlotStatus status
) {
}
