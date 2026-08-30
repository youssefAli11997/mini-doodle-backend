package com.example.doodle.meeting.persistence;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "meeting_participants")
public class MeetingParticipantEntity {

    @EmbeddedId
    private MeetingParticipantId id;

    public MeetingParticipantEntity() {
    }

    public MeetingParticipantEntity(MeetingParticipantId id) {
        this.id = id;
    }

    public MeetingParticipantEntity(UUID meetingId, UUID userId) {
        this.id = new MeetingParticipantId(meetingId, userId);
    }

    public MeetingParticipantId getId() {
        return id;
    }

    public void setId(MeetingParticipantId id) {
        this.id = id;
    }

    public UUID getMeetingId() {
        return id != null ? id.getMeetingId() : null;
    }

    public UUID getUserId() {
        return id != null ? id.getUserId() : null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MeetingParticipantEntity that = (MeetingParticipantEntity) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
