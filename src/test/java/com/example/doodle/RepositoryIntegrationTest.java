package com.example.doodle;

import com.example.doodle.meeting.persistence.MeetingEntity;
import com.example.doodle.meeting.persistence.MeetingParticipantEntity;
import com.example.doodle.meeting.persistence.MeetingParticipantId;
import com.example.doodle.meeting.persistence.MeetingParticipantRepository;
import com.example.doodle.meeting.persistence.MeetingRepository;
import com.example.doodle.slot.domain.SlotStatus;
import com.example.doodle.slot.persistence.TimeSlotEntity;
import com.example.doodle.slot.persistence.TimeSlotRepository;
import com.example.doodle.user.persistence.UserEntity;
import com.example.doodle.user.persistence.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RepositoryIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TimeSlotRepository timeSlotRepository;

    @Autowired
    private MeetingRepository meetingRepository;

    @Autowired
    private MeetingParticipantRepository meetingParticipantRepository;

    @BeforeEach
    void cleanDatabase() {
        meetingParticipantRepository.deleteAll();
        meetingRepository.deleteAll();
        timeSlotRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Save and retrieve UserEntity and verify Instant precision with PostgreSQL TIMESTAMPTZ")
    void saveAndRetrieveUser() {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);

        UserEntity user = new UserEntity(userId, "Alice Smith", "alice.smith@example.com", now, now);
        userRepository.save(user);

        Optional<UserEntity> retrieved = userRepository.findById(userId);
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getName()).isEqualTo("Alice Smith");
        assertThat(retrieved.get().getEmail()).isEqualTo("alice.smith@example.com");
        assertThat(retrieved.get().getCreatedAt()).isEqualTo(now);
        assertThat(retrieved.get().getUpdatedAt()).isEqualTo(now);

        assertThat(userRepository.findByEmail("alice.smith@example.com")).isPresent();
        assertThat(userRepository.existsByEmail("alice.smith@example.com")).isTrue();
        assertThat(userRepository.existsByEmail("nonexistent@example.com")).isFalse();
    }

    @Test
    @DisplayName("Save and retrieve TimeSlotEntity and test findByUserIdAndTimeRange filtering and ordering")
    void saveAndQueryTimeSlots() {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);

        UserEntity user = new UserEntity(userId, "Bob", "bob@example.com", now, now);
        userRepository.save(user);

        Instant t0900 = Instant.parse("2026-09-01T09:00:00Z");
        Instant t1000 = Instant.parse("2026-09-01T10:00:00Z");
        Instant t1000_2 = Instant.parse("2026-09-01T10:00:00Z");
        Instant t1100 = Instant.parse("2026-09-01T11:00:00Z");
        Instant t1100_2 = Instant.parse("2026-09-01T11:00:00Z");
        Instant t1200 = Instant.parse("2026-09-01T12:00:00Z");

        TimeSlotEntity slot1 = new TimeSlotEntity(UUID.randomUUID(), userId, t0900, t1000, SlotStatus.FREE, now, now);
        TimeSlotEntity slot2 = new TimeSlotEntity(UUID.randomUUID(), userId, t1000_2, t1100, SlotStatus.BUSY, now, now);
        TimeSlotEntity slot3 = new TimeSlotEntity(UUID.randomUUID(), userId, t1100_2, t1200, SlotStatus.FREE, now, now);

        timeSlotRepository.saveAll(List.of(slot1, slot2, slot3));

        // Query overlapping 09:30 to 11:30 without status filter -> should return slot1
        // (09-10), slot2 (10-11), slot3 (11-12)
        Instant queryFrom = Instant.parse("2026-09-01T09:30:00Z");
        Instant queryTo = Instant.parse("2026-09-01T11:30:00Z");

        List<TimeSlotEntity> allOverlapping = timeSlotRepository.findByUserIdAndTimeRange(userId, queryFrom, queryTo,
                null);
        assertThat(allOverlapping).hasSize(3);
        assertThat(allOverlapping.get(0).getId()).isEqualTo(slot1.getId());
        assertThat(allOverlapping.get(1).getId()).isEqualTo(slot2.getId());
        assertThat(allOverlapping.get(2).getId()).isEqualTo(slot3.getId());

        // Query with status = FREE -> should return slot1 and slot3
        List<TimeSlotEntity> freeOverlapping = timeSlotRepository.findByUserIdAndTimeRange(userId, queryFrom, queryTo,
                SlotStatus.FREE);
        assertThat(freeOverlapping).hasSize(2);
        assertThat(freeOverlapping.get(0).getId()).isEqualTo(slot1.getId());
        assertThat(freeOverlapping.get(1).getId()).isEqualTo(slot3.getId());

        // Query with status = BUSY -> should return slot2
        List<TimeSlotEntity> busyOverlapping = timeSlotRepository.findByUserIdAndTimeRange(userId, queryFrom, queryTo,
                SlotStatus.BUSY);
        assertThat(busyOverlapping).hasSize(1);
        assertThat(busyOverlapping.get(0).getId()).isEqualTo(slot2.getId());

        // findByIdAndUserId
        assertThat(timeSlotRepository.findByIdAndUserId(slot1.getId(), userId)).isPresent();
        assertThat(timeSlotRepository.findByIdAndUserId(slot1.getId(), UUID.randomUUID())).isEmpty();
    }

    @Test
    @Transactional
    @DisplayName("Verify findByIdForUpdate pessimistic lock query on TimeSlot")
    void testFindByIdForUpdate() {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);

        UserEntity user = new UserEntity(userId, "Charlie", "charlie@example.com", now, now);
        userRepository.save(user);

        Instant start = Instant.parse("2026-09-01T09:00:00Z");
        Instant end = Instant.parse("2026-09-01T10:00:00Z");
        UUID slotId = UUID.randomUUID();

        TimeSlotEntity slot = new TimeSlotEntity(slotId, userId, start, end, SlotStatus.FREE, now, now);
        timeSlotRepository.save(slot);

        Optional<TimeSlotEntity> lockedSlot = timeSlotRepository.findByIdForUpdate(slotId);
        assertThat(lockedSlot).isPresent();
        assertThat(lockedSlot.get().getStatus()).isEqualTo(SlotStatus.FREE);
    }

    @Test
    @DisplayName("Save and retrieve MeetingEntity and MeetingParticipantEntity")
    void saveAndRetrieveMeetingWithParticipants() {
        UUID ownerId = UUID.randomUUID();
        UUID participant2Id = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();
        UUID meetingId = UUID.randomUUID();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);

        UserEntity owner = new UserEntity(ownerId, "Owner", "owner@example.com", now, now);
        UserEntity participant2 = new UserEntity(participant2Id, "Participant", "p2@example.com", now, now);
        userRepository.saveAll(List.of(owner, participant2));

        Instant start = Instant.parse("2026-09-01T09:00:00Z");
        Instant end = Instant.parse("2026-09-01T10:00:00Z");
        TimeSlotEntity slot = new TimeSlotEntity(slotId, ownerId, start, end, SlotStatus.BUSY, now, now);
        timeSlotRepository.save(slot);

        MeetingEntity meeting = new MeetingEntity(meetingId, slotId, "Design Review", "Architecture discussion", now,
                now);
        meetingRepository.save(meeting);

        MeetingParticipantEntity p1 = new MeetingParticipantEntity(meetingId, ownerId);
        MeetingParticipantEntity p2 = new MeetingParticipantEntity(meetingId, participant2Id);
        meetingParticipantRepository.saveAll(List.of(p1, p2));

        // Verify meeting retrieval
        Optional<MeetingEntity> retrievedMeeting = meetingRepository.findById(meetingId);
        assertThat(retrievedMeeting).isPresent();
        assertThat(retrievedMeeting.get().getTitle()).isEqualTo("Design Review");
        assertThat(retrievedMeeting.get().getDescription()).isEqualTo("Architecture discussion");
        assertThat(retrievedMeeting.get().getSlotId()).isEqualTo(slotId);

        // Verify meeting retrieval by slotId
        Optional<MeetingEntity> bySlot = meetingRepository.findBySlotId(slotId);
        assertThat(bySlot).isPresent();
        assertThat(bySlot.get().getId()).isEqualTo(meetingId);

        // Verify participants retrieval
        List<MeetingParticipantEntity> participants = meetingParticipantRepository.findByIdMeetingId(meetingId);
        assertThat(participants).hasSize(2);
        assertThat(participants).extracting(MeetingParticipantEntity::getUserId).containsExactlyInAnyOrder(ownerId,
                participant2Id);

        List<MeetingParticipantEntity> userMeetings = meetingParticipantRepository.findByIdUserId(participant2Id);
        assertThat(userMeetings).hasSize(1);
        assertThat(userMeetings.get(0).getMeetingId()).isEqualTo(meetingId);
    }
}
