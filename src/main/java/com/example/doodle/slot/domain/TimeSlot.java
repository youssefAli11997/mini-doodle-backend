package com.example.doodle.slot.domain;

import java.time.Instant;
import java.util.UUID;

public record TimeSlot(
        UUID id,
        UUID userId,
        Instant startTime,
        Instant endTime,
        SlotStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    public static TimeSlot createFree(UUID userId, Instant startTime, Instant endTime) {
        if (startTime == null || endTime == null) {
            throw new IllegalArgumentException("Start time and end time are required");
        }
        if (!startTime.isBefore(endTime)) {
            throw new IllegalArgumentException("Start time must be before end time");
        }
        Instant now = Instant.now();
        return new TimeSlot(UUID.randomUUID(), userId, startTime, endTime, SlotStatus.FREE, now, now);
    }

    public boolean isFree() {
        return status == SlotStatus.FREE;
    }

    public boolean isBusy() {
        return status == SlotStatus.BUSY;
    }

    public TimeSlot toBusy() {
        return new TimeSlot(id, userId, startTime, endTime, SlotStatus.BUSY, createdAt, Instant.now());
    }

    public TimeSlot updateRange(Instant newStart, Instant newEnd) {
        if (newStart == null || newEnd == null) {
            throw new IllegalArgumentException("Start time and end time are required");
        }
        if (!newStart.isBefore(newEnd)) {
            throw new IllegalArgumentException("Start time must be before end time");
        }
        return new TimeSlot(id, userId, newStart, newEnd, status, createdAt, Instant.now());
    }
}
