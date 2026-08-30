package com.example.doodle;

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
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class BookMeetingAcceptanceTest extends BaseIntegrationTest {

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
    private UUID user2Id;
    private UUID user3Id;
    private UUID slotId;

    @BeforeEach
    void setup() {
        meetingParticipantRepository.deleteAll();
        meetingRepository.deleteAll();
        timeSlotRepository.deleteAll();
        userRepository.deleteAll();

        User owner = User.create("Owner", "owner@example.com");
        User user2 = User.create("User 2", "user2@example.com");
        User user3 = User.create("User 3", "user3@example.com");

        userRepository.saveAll(List.of(
                new UserEntity(owner.id(), owner.name(), owner.email(), owner.createdAt(), owner.updatedAt()),
                new UserEntity(user2.id(), user2.name(), user2.email(), user2.createdAt(), user2.updatedAt()),
                new UserEntity(user3.id(), user3.name(), user3.email(), user3.createdAt(), user3.updatedAt())
        ));

        this.ownerId = owner.id();
        this.user2Id = user2.id();
        this.user3Id = user3.id();

        Instant start = Instant.parse("2026-09-01T09:00:00Z");
        Instant end = Instant.parse("2026-09-01T10:00:00Z");
        Instant now = Instant.now();
        TimeSlotEntity slot = new TimeSlotEntity(UUID.randomUUID(), ownerId, start, end, SlotStatus.FREE, now, now);
        timeSlotRepository.save(slot);
        this.slotId = slot.getId();
    }

    @Test
    @DisplayName("POST /users/{ownerId}/slots/{slotId}/meeting books a meeting atomically and marks slot BUSY")
    void bookMeetingSuccessfully() {
        BookMeetingRequest request = new BookMeetingRequest(
                "Sprint Planning",
                "Discuss upcoming sprint goals",
                List.of(ownerId, user2Id, user3Id)
        );

        ResponseEntity<MeetingResponse> response = restTemplate.postForEntity(
                "/users/" + ownerId + "/slots/" + slotId + "/meeting",
                request,
                MeetingResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().id()).isNotNull();
        assertThat(response.getBody().title()).isEqualTo("Sprint Planning");
        assertThat(response.getBody().slotId()).isEqualTo(slotId);
        assertThat(response.getBody().participantIds()).containsExactlyInAnyOrder(ownerId, user2Id, user3Id);

        // Verify slot status is now BUSY
        TimeSlotEntity slot = timeSlotRepository.findById(slotId).orElseThrow();
        assertThat(slot.getStatus()).isEqualTo(SlotStatus.BUSY);

        // Verify meeting persisted in DB
        assertThat(meetingRepository.findById(response.getBody().id())).isPresent();
        assertThat(meetingParticipantRepository.findByIdMeetingId(response.getBody().id())).hasSize(3);
    }

    @Test
    @DisplayName("Booking a BUSY slot returns 409 Conflict")
    void bookBusySlotReturnsConflict() {
        BookMeetingRequest request1 = new BookMeetingRequest(
                "First Meeting",
                "Desc",
                List.of(ownerId, user2Id)
        );
        restTemplate.postForEntity("/users/" + ownerId + "/slots/" + slotId + "/meeting", request1, MeetingResponse.class);

        // Attempt second booking
        BookMeetingRequest request2 = new BookMeetingRequest(
                "Second Meeting",
                "Desc",
                List.of(ownerId, user3Id)
        );
        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                "/users/" + ownerId + "/slots/" + slotId + "/meeting",
                request2,
                ErrorResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("SLOT_ALREADY_BOOKED");
    }

    @Test
    @DisplayName("Booking without owner in participants returns 400 Bad Request")
    void bookWithoutOwnerReturnsBadRequest() {
        BookMeetingRequest request = new BookMeetingRequest(
                "Meeting",
                "Desc",
                List.of(user2Id, user3Id)
        );
        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                "/users/" + ownerId + "/slots/" + slotId + "/meeting",
                request,
                ErrorResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("INVALID_PARTICIPANTS");
    }

    @Test
    @DisplayName("Booking with only owner returns 400 Bad Request")
    void bookWithOnlyOwnerReturnsBadRequest() {
        BookMeetingRequest request = new BookMeetingRequest(
                "Solo Meeting",
                "Desc",
                List.of(ownerId)
        );
        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                "/users/" + ownerId + "/slots/" + slotId + "/meeting",
                request,
                ErrorResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("INVALID_PARTICIPANTS");
    }

    @Test
    @DisplayName("Booking with duplicate participant IDs returns 400 Bad Request")
    void bookWithDuplicateParticipantsReturnsBadRequest() {
        BookMeetingRequest request = new BookMeetingRequest(
                "Meeting",
                "Desc",
                List.of(ownerId, user2Id, user2Id)
        );
        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                "/users/" + ownerId + "/slots/" + slotId + "/meeting",
                request,
                ErrorResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("INVALID_PARTICIPANTS");
    }

    @Test
    @DisplayName("Booking with non-existent participant returns 400 Bad Request")
    void bookWithNonExistentParticipantReturnsBadRequest() {
        BookMeetingRequest request = new BookMeetingRequest(
                "Meeting",
                "Desc",
                List.of(ownerId, UUID.randomUUID())
        );
        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                "/users/" + ownerId + "/slots/" + slotId + "/meeting",
                request,
                ErrorResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("INVALID_PARTICIPANTS");
    }

    @Test
    @DisplayName("Booking for non-existent slot returns 404 Not Found")
    void bookNonExistentSlotReturnsNotFound() {
        BookMeetingRequest request = new BookMeetingRequest(
                "Meeting",
                "Desc",
                List.of(ownerId, user2Id)
        );
        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                "/users/" + ownerId + "/slots/" + UUID.randomUUID() + "/meeting",
                request,
                ErrorResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().code()).isEqualTo("SLOT_NOT_FOUND");
    }

    @Test
    @DisplayName("Booking for slot belonging to another user returns 404 Not Found")
    void bookSlotBelongingToAnotherUserReturnsNotFound() {
        BookMeetingRequest request = new BookMeetingRequest(
                "Meeting",
                "Desc",
                List.of(user2Id, ownerId)
        );
        // user2 tries to book owner's slot
        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                "/users/" + user2Id + "/slots/" + slotId + "/meeting",
                request,
                ErrorResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().code()).isEqualTo("SLOT_NOT_FOUND");
    }

    @Test
    @DisplayName("Concurrency test: 10 concurrent bookings for same slot -> exactly 1 winner, 9 conflicts")
    void concurrentBookingSafety() throws InterruptedException, ExecutionException {
        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Callable<Integer>> tasks = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            final int index = i;
            tasks.add(() -> {
                BookMeetingRequest request = new BookMeetingRequest(
                        "Concurrent Meeting " + index,
                        "Concurrent Desc",
                        List.of(ownerId, user2Id)
                );
                ResponseEntity<MeetingResponse> resp = restTemplate.postForEntity(
                        "/users/" + ownerId + "/slots/" + slotId + "/meeting",
                        request,
                        MeetingResponse.class
                );
                return resp.getStatusCode().value();
            });
        }

        List<Future<Integer>> futures = executor.invokeAll(tasks);
        executor.shutdown();

        int successCount = 0;
        int conflictCount = 0;
        for (Future<Integer> f : futures) {
            int code = f.get();
            if (code == 201) successCount++;
            else if (code == 409) conflictCount++;
        }

        assertThat(successCount).isEqualTo(1);
        assertThat(conflictCount).isEqualTo(threads - 1);
        assertThat(meetingRepository.findAll()).hasSize(1);
        assertThat(timeSlotRepository.findById(slotId).orElseThrow().getStatus()).isEqualTo(SlotStatus.BUSY);
    }
}
