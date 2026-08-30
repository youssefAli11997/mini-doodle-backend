package com.example.doodle.availability.domain;

import com.example.doodle.slot.domain.SlotStatus;

import java.time.Instant;

public record AvailabilityPeriod(
        Instant startTime,
        Instant endTime,
        SlotStatus status
) {
    public AvailabilityPeriod {
        if (startTime == null || endTime == null) {
            throw new IllegalArgumentException("Start time and end time are required");
        }
        if (!startTime.isBefore(endTime)) {
            throw new IllegalArgumentException("Start time must be before end time");
        }
        if (status == null) {
            throw new IllegalArgumentException("Slot status is required");
        }
    }
}
