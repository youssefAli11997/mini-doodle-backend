package com.example.doodle.meeting.application;

import com.example.doodle.meeting.domain.Meeting;
import com.example.doodle.meeting.persistence.MeetingEntity;

import java.util.List;
import java.util.UUID;

public final class MeetingMapper {

    private MeetingMapper() {
    }

    public static Meeting toDomain(MeetingEntity entity, List<UUID> participantIds) {
        if (entity == null) {
            return null;
        }
        return new Meeting(
                entity.getId(),
                entity.getSlotId(),
                entity.getTitle(),
                entity.getDescription(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                participantIds != null ? List.copyOf(participantIds) : List.of()
        );
    }

    public static MeetingEntity toEntity(Meeting domain) {
        if (domain == null) {
            return null;
        }
        return new MeetingEntity(
                domain.id(),
                domain.slotId(),
                domain.title(),
                domain.description(),
                domain.createdAt(),
                domain.updatedAt()
        );
    }

    public static MeetingDto toDto(Meeting domain) {
        if (domain == null) {
            return null;
        }
        return new MeetingDto(
                domain.id(),
                domain.slotId(),
                domain.title(),
                domain.description(),
                domain.createdAt(),
                domain.updatedAt(),
                domain.participantIds()
        );
    }
}
