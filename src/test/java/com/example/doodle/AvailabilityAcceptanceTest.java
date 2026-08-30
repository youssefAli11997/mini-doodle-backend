package com.example.doodle;

import com.example.doodle.availability.api.AvailabilityPeriodResponse;
import com.example.doodle.common.exception.ErrorResponse;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AvailabilityAcceptanceTest extends BaseIntegrationTest {

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
    @DisplayName("GET /users/{userId}/availability aggregates adjacent same-status periods, preserves gaps, and clips bounds")
    void getAggregatedAvailability() {
        Instant now = Instant.now();
        /*
          Setup slots:
          09:00 - 10:00 FREE
          10:00 - 11:00 FREE  (adjacent FREE -> should merge to 09:00 - 11:00)
          11:00 - 12:00 BUSY  (adjacent BUSY)
          12:00 - 13:00 FREE  (gap from 14:00)
          14:00 - 15:00 FREE
          15:00 - 16:00 FREE  (adjacent FREE -> should merge to 14:00 - 16:00)
        */
        Instant t0900 = Instant.parse("2026-09-01T09:00:00Z");
        Instant t1000 = Instant.parse("2026-09-01T10:00:00Z");
        Instant t1100 = Instant.parse("2026-09-01T11:00:00Z");
        Instant t1200 = Instant.parse("2026-09-01T12:00:00Z");
        Instant t1300 = Instant.parse("2026-09-01T13:00:00Z");
        Instant t1400 = Instant.parse("2026-09-01T14:00:00Z");
        Instant t1500 = Instant.parse("2026-09-01T15:00:00Z");
        Instant t1600 = Instant.parse("2026-09-01T16:00:00Z");

        timeSlotRepository.saveAll(List.of(
                new TimeSlotEntity(UUID.randomUUID(), userId, t0900, t1000, SlotStatus.FREE, now, now),
                new TimeSlotEntity(UUID.randomUUID(), userId, t1000, t1100, SlotStatus.FREE, now, now),
                new TimeSlotEntity(UUID.randomUUID(), userId, t1100, t1200, SlotStatus.BUSY, now, now),
                new TimeSlotEntity(UUID.randomUUID(), userId, t1200, t1300, SlotStatus.FREE, now, now),
                new TimeSlotEntity(UUID.randomUUID(), userId, t1400, t1500, SlotStatus.FREE, now, now),
                new TimeSlotEntity(UUID.randomUUID(), userId, t1500, t1600, SlotStatus.FREE, now, now)
        ));

        // Query 09:30 to 15:30 (tests clipping of 09:00-11:00 to 09:30-11:00, and 14:00-16:00 to 14:00-15:30)
        String url = String.format("/users/%s/availability?from=2026-09-01T09:30:00Z&to=2026-09-01T15:30:00Z", userId);
        ResponseEntity<AvailabilityPeriodResponse[]> response = restTemplate.getForEntity(url, AvailabilityPeriodResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        AvailabilityPeriodResponse[] periods = response.getBody();

        assertThat(periods).hasSize(4);

        // 1. Merged FREE 09:30 - 11:00
        assertThat(periods[0].startTime()).isEqualTo(Instant.parse("2026-09-01T09:30:00Z"));
        assertThat(periods[0].endTime()).isEqualTo(t1100);
        assertThat(periods[0].status()).isEqualTo(SlotStatus.FREE);

        // 2. BUSY 11:00 - 12:00
        assertThat(periods[1].startTime()).isEqualTo(t1100);
        assertThat(periods[1].endTime()).isEqualTo(t1200);
        assertThat(periods[1].status()).isEqualTo(SlotStatus.BUSY);

        // 3. FREE 12:00 - 13:00
        assertThat(periods[2].startTime()).isEqualTo(t1200);
        assertThat(periods[2].endTime()).isEqualTo(t1300);
        assertThat(periods[2].status()).isEqualTo(SlotStatus.FREE);

        // (Gap 13:00 - 14:00 preserved)

        // 4. Merged FREE 14:00 - 15:30 (clipped at 15:30)
        assertThat(periods[3].startTime()).isEqualTo(t1400);
        assertThat(periods[3].endTime()).isEqualTo(Instant.parse("2026-09-01T15:30:00Z"));
        assertThat(periods[3].status()).isEqualTo(SlotStatus.FREE);
    }

    @Test
    @DisplayName("GET /users/{userId}/availability with status=FREE returns only FREE periods")
    void getAvailabilityFilteredByStatus() {
        Instant now = Instant.now();
        Instant t0900 = Instant.parse("2026-09-01T09:00:00Z");
        Instant t1000 = Instant.parse("2026-09-01T10:00:00Z");
        Instant t1100 = Instant.parse("2026-09-01T11:00:00Z");
        Instant t1200 = Instant.parse("2026-09-01T12:00:00Z");

        timeSlotRepository.saveAll(List.of(
                new TimeSlotEntity(UUID.randomUUID(), userId, t0900, t1000, SlotStatus.FREE, now, now),
                new TimeSlotEntity(UUID.randomUUID(), userId, t1000, t1100, SlotStatus.BUSY, now, now),
                new TimeSlotEntity(UUID.randomUUID(), userId, t1100, t1200, SlotStatus.FREE, now, now)
        ));

        String url = String.format("/users/%s/availability?from=2026-09-01T08:00:00Z&to=2026-09-01T13:00:00Z&status=FREE", userId);
        ResponseEntity<AvailabilityPeriodResponse[]> response = restTemplate.getForEntity(url, AvailabilityPeriodResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).hasSize(2);
        assertThat(response.getBody()[0].status()).isEqualTo(SlotStatus.FREE);
        assertThat(response.getBody()[1].status()).isEqualTo(SlotStatus.FREE);
    }

    @Test
    @DisplayName("GET /users/{userId}/availability with missing params returns 400 Bad Request")
    void getAvailabilityMissingParams() {
        String url = String.format("/users/%s/availability", userId);
        ResponseEntity<ErrorResponse> response = restTemplate.getForEntity(url, ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("GET /users/{userId}/availability with from >= to returns 400 Bad Request")
    void getAvailabilityInvalidRange() {
        String url = String.format("/users/%s/availability?from=2026-09-01T12:00:00Z&to=2026-09-01T09:00:00Z", userId);
        ResponseEntity<ErrorResponse> response = restTemplate.getForEntity(url, ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("INVALID_TIME_RANGE");
    }
}
