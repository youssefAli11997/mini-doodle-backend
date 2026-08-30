package com.example.doodle;

import com.example.doodle.common.exception.ErrorResponse;
import com.example.doodle.meeting.api.BookMeetingRequest;
import com.example.doodle.meeting.api.MeetingResponse;
import com.example.doodle.meeting.persistence.MeetingParticipantRepository;
import com.example.doodle.meeting.persistence.MeetingRepository;
import com.example.doodle.slot.api.CreateSlotRequest;
import com.example.doodle.slot.application.SlotDto;
import com.example.doodle.slot.persistence.TimeSlotRepository;
import com.example.doodle.user.domain.User;
import com.example.doodle.user.persistence.UserEntity;
import com.example.doodle.user.persistence.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GetMeetingAcceptanceTest extends BaseIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

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
    private UUID unrelatedUserId;
    private UUID meetingId;

    @BeforeEach
    void setup() {
        meetingParticipantRepository.deleteAll();
        meetingRepository.deleteAll();
        timeSlotRepository.deleteAll();
        userRepository.deleteAll();

        User owner = User.create("Owner", "owner@example.com");
        User participant = User.create("Participant", "participant@example.com");
        User unrelated = User.create("Unrelated", "unrelated@example.com");

        userRepository.saveAll(List.of(
                new UserEntity(owner.id(), owner.name(), owner.email(), owner.createdAt(), owner.updatedAt()),
                new UserEntity(participant.id(), participant.name(), participant.email(), participant.createdAt(), participant.updatedAt()),
                new UserEntity(unrelated.id(), unrelated.name(), unrelated.email(), unrelated.createdAt(), unrelated.updatedAt())
        ));

        this.ownerId = owner.id();
        this.participantId = participant.id();
        this.unrelatedUserId = unrelated.id();

        // Create slot
        CreateSlotRequest slotReq = new CreateSlotRequest(
                Instant.parse("2026-09-01T09:00:00Z"),
                Instant.parse("2026-09-01T10:00:00Z")
        );
        ResponseEntity<SlotDto> slotResp = restTemplate.postForEntity("/users/" + ownerId + "/slots", slotReq, SlotDto.class);
        UUID slotId = slotResp.getBody().id();

        // Book meeting
        BookMeetingRequest bookReq = new BookMeetingRequest("Architecture Review", "Discuss design", List.of(ownerId, participantId));
        ResponseEntity<MeetingResponse> meetingResp = restTemplate.postForEntity(
                "/users/" + ownerId + "/slots/" + slotId + "/meeting",
                bookReq,
                MeetingResponse.class
        );
        this.meetingId = meetingResp.getBody().id();
    }

    @Test
    @DisplayName("GET /users/{userId}/meetings/{meetingId} returns 200 OK for owner or participant")
    void getMeetingSuccessfully() {
        // Owner gets meeting
        ResponseEntity<MeetingResponse> ownerResp = restTemplate.getForEntity(
                "/users/" + ownerId + "/meetings/" + meetingId,
                MeetingResponse.class
        );
        assertThat(ownerResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ownerResp.getBody()).isNotNull();
        assertThat(ownerResp.getBody().id()).isEqualTo(meetingId);
        assertThat(ownerResp.getBody().title()).isEqualTo("Architecture Review");
        assertThat(ownerResp.getBody().participantIds()).containsExactlyInAnyOrder(ownerId, participantId);
        assertThat(ownerResp.getBody().slot()).isNotNull();

        // Participant gets meeting
        ResponseEntity<MeetingResponse> participantResp = restTemplate.getForEntity(
                "/users/" + participantId + "/meetings/" + meetingId,
                MeetingResponse.class
        );
        assertThat(participantResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(participantResp.getBody().id()).isEqualTo(meetingId);
    }

    @Test
    @DisplayName("GET /users/{userId}/meetings/{meetingId} returns 404 for unrelated user or missing meeting")
    void getMeetingNotFound() {
        // Unrelated user tries to get meeting -> 404
        ResponseEntity<ErrorResponse> unrelatedResp = restTemplate.getForEntity(
                "/users/" + unrelatedUserId + "/meetings/" + meetingId,
                ErrorResponse.class
        );
        assertThat(unrelatedResp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(unrelatedResp.getBody().code()).isEqualTo("MEETING_NOT_FOUND");

        // Non-existent meeting ID -> 404
        ResponseEntity<ErrorResponse> missingResp = restTemplate.getForEntity(
                "/users/" + ownerId + "/meetings/" + UUID.randomUUID(),
                ErrorResponse.class
        );
        assertThat(missingResp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(missingResp.getBody().code()).isEqualTo("MEETING_NOT_FOUND");
    }
}
