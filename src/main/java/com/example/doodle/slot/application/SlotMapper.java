package com.example.doodle.slot.application;

import com.example.doodle.slot.domain.TimeSlot;
import com.example.doodle.slot.persistence.TimeSlotEntity;

public final class SlotMapper {

    private SlotMapper() {
    }

    public static TimeSlot toDomain(TimeSlotEntity entity) {
        if (entity == null) {
            return null;
        }
        return new TimeSlot(
                entity.getId(),
                entity.getUserId(),
                entity.getStartTime(),
                entity.getEndTime(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public static TimeSlotEntity toEntity(TimeSlot domain) {
        if (domain == null) {
            return null;
        }
        return new TimeSlotEntity(
                domain.id(),
                domain.userId(),
                domain.startTime(),
                domain.endTime(),
                domain.status(),
                domain.createdAt(),
                domain.updatedAt()
        );
    }

    public static SlotDto toDto(TimeSlot domain) {
        if (domain == null) {
            return null;
        }
        return new SlotDto(
                domain.id(),
                domain.userId(),
                domain.startTime(),
                domain.endTime(),
                domain.status(),
                domain.createdAt(),
                domain.updatedAt()
        );
    }
}
