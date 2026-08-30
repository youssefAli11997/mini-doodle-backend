package com.example.doodle;

import com.example.doodle.common.exception.ErrorResponse;
import com.example.doodle.slot.api.CreateSlotRequest;
import com.example.doodle.slot.application.SlotDto;
import com.example.doodle.slot.domain.SlotStatus;
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

class CreateSlotAcceptanceTest extends BaseIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TimeSlotRepository timeSlotRepository;

    private UUID userId;

    @BeforeEach
    void setup() {
        timeSlotRepository.deleteAll();
        userRepository.deleteAll();

        User user = User.create("Alice", "alice@example.com");
        userRepository.save(new UserEntity(user.id(), user.name(), user.email(), user.createdAt(), user.updatedAt()));
        this.userId = user.id();
    }

    @Test
    @DisplayName("POST /users/{userId}/slots creates a FREE slot and returns 201")
    void createFreeSlotSuccessfully() {
        Instant start = Instant.parse("2026-09-01T09:00:00Z");
        Instant end = Instant.parse("2026-09-01T10:00:00Z");
        CreateSlotRequest request = new CreateSlotRequest(start, end);

        ResponseEntity<SlotDto> response = restTemplate.postForEntity("/users/" + userId + "/slots", request, SlotDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().id()).isNotNull();
        assertThat(response.getBody().userId()).isEqualTo(userId);
        assertThat(response.getBody().startTime()).isEqualTo(start);
        assertThat(response.getBody().endTime()).isEqualTo(end);
        assertThat(response.getBody().status()).isEqualTo(SlotStatus.FREE);
    }

    @Test
    @DisplayName("POST /users/{userId}/slots with start >= end returns 400 Bad Request")
    void createInvalidTimeRangeReturnsBadRequest() {
        Instant start = Instant.parse("2026-09-01T10:00:00Z");
        Instant end = Instant.parse("2026-09-01T09:00:00Z");
        CreateSlotRequest request = new CreateSlotRequest(start, end);

        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity("/users/" + userId + "/slots", request, ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("INVALID_TIME_RANGE");
    }

    @Test
    @DisplayName("POST /users/{userId}/slots for non-existent user returns 404 Not Found")
    void createSlotForNonExistentUserReturnsNotFound() {
        UUID nonExistentUserId = UUID.randomUUID();
        CreateSlotRequest request = new CreateSlotRequest(
                Instant.parse("2026-09-01T09:00:00Z"),
                Instant.parse("2026-09-01T10:00:00Z")
        );

        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity("/users/" + nonExistentUserId + "/slots", request, ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("USER_NOT_FOUND");
    }

    @Test
    @DisplayName("POST /users/{userId}/slots with overlapping time returns 409 Conflict")
    void createOverlappingSlotReturnsConflict() {
        CreateSlotRequest slot1 = new CreateSlotRequest(
                Instant.parse("2026-09-01T09:00:00Z"),
                Instant.parse("2026-09-01T10:00:00Z")
        );
        restTemplate.postForEntity("/users/" + userId + "/slots", slot1, SlotDto.class);

        // Overlapping slot 09:30 - 10:30
        CreateSlotRequest overlapping = new CreateSlotRequest(
                Instant.parse("2026-09-01T09:30:00Z"),
                Instant.parse("2026-09-01T10:30:00Z")
        );
        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity("/users/" + userId + "/slots", overlapping, ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("SLOT_OVERLAP");
    }

    @Test
    @DisplayName("POST /users/{userId}/slots allows adjacent slots for same user")
    void createAdjacentSlotsAllowed() {
        CreateSlotRequest slot1 = new CreateSlotRequest(
                Instant.parse("2026-09-01T09:00:00Z"),
                Instant.parse("2026-09-01T10:00:00Z")
        );
        ResponseEntity<SlotDto> resp1 = restTemplate.postForEntity("/users/" + userId + "/slots", slot1, SlotDto.class);
        assertThat(resp1.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Adjacent slot: 10:00 - 11:00
        CreateSlotRequest slot2 = new CreateSlotRequest(
                Instant.parse("2026-09-01T10:00:00Z"),
                Instant.parse("2026-09-01T11:00:00Z")
        );
        ResponseEntity<SlotDto> resp2 = restTemplate.postForEntity("/users/" + userId + "/slots", slot2, SlotDto.class);
        assertThat(resp2.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("Concurrent slot creation for same user with overlapping times — exactly one succeeds")
    void concurrentOverlappingSlotCreation() throws InterruptedException, ExecutionException {
        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Callable<Integer>> tasks = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            tasks.add(() -> {
                CreateSlotRequest request = new CreateSlotRequest(
                        Instant.parse("2026-09-01T14:00:00Z"),
                        Instant.parse("2026-09-01T15:00:00Z")
                );
                ResponseEntity<SlotDto> resp = restTemplate.postForEntity("/users/" + userId + "/slots", request, SlotDto.class);
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
        assertThat(timeSlotRepository.findByUserIdAndTimeRange(userId, Instant.parse("2026-09-01T13:00:00Z"), Instant.parse("2026-09-01T16:00:00Z"), null)).hasSize(1);
    }
}
