package com.example.doodle;

import com.example.doodle.availability.api.AvailabilityPeriodResponse;
import com.example.doodle.common.exception.ErrorResponse;
import com.example.doodle.meeting.api.BookMeetingRequest;
import com.example.doodle.meeting.api.MeetingResponse;
import com.example.doodle.meeting.persistence.MeetingParticipantRepository;
import com.example.doodle.meeting.persistence.MeetingRepository;
import com.example.doodle.slot.domain.SlotStatus;
import com.example.doodle.slot.persistence.TimeSlotEntity;
import com.example.doodle.slot.persistence.TimeSlotRepository;
import com.example.doodle.user.domain.User;
import com.example.doodle.user.persistence.UserEntity;
import com.example.doodle.user.persistence.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ObservabilityAcceptanceTest extends BaseIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TimeSlotRepository timeSlotRepository;

    @Autowired
    private MeetingRepository meetingRepository;

    @Autowired
    private MeetingParticipantRepository meetingParticipantRepository;

    private UUID ownerId;
    private UUID participantId;
    private UUID slotId;

    @BeforeEach
    void setup() {
        meetingParticipantRepository.deleteAll();
        meetingRepository.deleteAll();
        timeSlotRepository.deleteAll();
        userRepository.deleteAll();

        User owner = User.create("Owner", "owner@example.com");
        User participant = User.create("Participant", "participant@example.com");
        userRepository.saveAll(List.of(
                new UserEntity(owner.id(), owner.name(), owner.email(), owner.createdAt(), owner.updatedAt()),
                new UserEntity(participant.id(), participant.name(), participant.email(), participant.createdAt(), participant.updatedAt())
        ));
        ownerId = owner.id();
        participantId = participant.id();

        Instant now = Instant.now();
        TimeSlotEntity slot = new TimeSlotEntity(
                UUID.randomUUID(),
                ownerId,
                Instant.parse("2026-09-01T09:00:00Z"),
                Instant.parse("2026-09-01T10:00:00Z"),
                SlotStatus.FREE,
                now,
                now
        );
        timeSlotRepository.save(slot);
        slotId = slot.getId();
    }

    @Test
    @DisplayName("/actuator/prometheus exposes scheduling and HTTP request metrics")
    void prometheusExposesSchedulingMetrics() {
        BookMeetingRequest booking = new BookMeetingRequest(
                "Metrics Review",
                "Exercise booking counters",
                List.of(ownerId, participantId)
        );

        ResponseEntity<MeetingResponse> booked = restTemplate.postForEntity(
                "/users/" + ownerId + "/slots/" + slotId + "/meeting",
                booking,
                MeetingResponse.class
        );
        assertThat(booked.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<ErrorResponse> conflict = restTemplate.postForEntity(
                "/users/" + ownerId + "/slots/" + slotId + "/meeting",
                booking,
                ErrorResponse.class
        );
        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<AvailabilityPeriodResponse[]> availability = restTemplate.getForEntity(
                "/users/" + ownerId + "/availability?from=2026-09-01T08:00:00Z&to=2026-09-01T11:00:00Z",
                AvailabilityPeriodResponse[].class
        );
        assertThat(availability.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> prometheus = restTemplate.getForEntity("/actuator/prometheus", String.class);

        assertThat(prometheus.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(prometheus.getBody())
                .contains("meeting_booking_success_total")
                .contains("meeting_booking_conflict_total")
                .contains("availability_query_total")
                .contains("http_server_requests_seconds_count");
    }

    @Test
    @DisplayName("Malformed request parameters return consistent error JSON")
    void malformedParamsReturnConsistentErrorJson() {
        ResponseEntity<ErrorResponse> response = restTemplate.getForEntity(
                "/users/" + ownerId + "/availability?from=2026-09-01T08:00:00Z&to=2026-09-01T11:00:00Z&status=UNKNOWN",
                ErrorResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("BAD_REQUEST");
        assertThat(response.getBody().message()).contains("status");
    }
}
