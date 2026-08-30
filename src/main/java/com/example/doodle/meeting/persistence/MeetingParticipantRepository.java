package com.example.doodle.meeting.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MeetingParticipantRepository extends JpaRepository<MeetingParticipantEntity, MeetingParticipantId> {

    List<MeetingParticipantEntity> findByIdMeetingId(UUID meetingId);

    List<MeetingParticipantEntity> findByIdUserId(UUID userId);
}
