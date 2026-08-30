package com.example.doodle;

import com.example.doodle.common.exception.ErrorResponse;
import com.example.doodle.slot.api.CreateSlotRequest;
import com.example.doodle.slot.api.UpdateSlotRequest;
import com.example.doodle.slot.application.SlotDto;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SlotManagementAcceptanceTest extends BaseIntegrationTest {

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
    @DisplayName("GET /users/{userId}/slots with timeframe clips partial overlaps")
    void listSlotsWithClipping() {
        // Slot 09:00 - 11:00
        CreateSlotRequest slotReq = new CreateSlotRequest(
                Instant.parse("2026-09-01T09:00:00Z"),
                Instant.parse("2026-09-01T11:00:00Z")
        );
        restTemplate.postForEntity("/users/" + userId + "/slots", slotReq, SlotDto.class);

        // Query 10:00 - 12:00
        String url = String.format("/users/%s/slots?from=2026-09-01T10:00:00Z&to=2026-09-01T12:00:00Z", userId);
        ResponseEntity<SlotDto[]> response = restTemplate.getForEntity(url, SlotDto[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody()[0].startTime()).isEqualTo(Instant.parse("2026-09-01T10:00:00Z"));
        assertThat(response.getBody()[0].endTime()).isEqualTo(Instant.parse("2026-09-01T11:00:00Z"));
    }

    @Test
    @DisplayName("GET /users/{userId}/slots/{slotId} returns slot or 404")
    void getSingleSlot() {
        CreateSlotRequest slotReq = new CreateSlotRequest(
                Instant.parse("2026-09-01T09:00:00Z"),
                Instant.parse("2026-09-01T10:00:00Z")
        );
        ResponseEntity<SlotDto> created = restTemplate.postForEntity("/users/" + userId + "/slots", slotReq, SlotDto.class);
        UUID slotId = created.getBody().id();

        ResponseEntity<SlotDto> response = restTemplate.getForEntity("/users/" + userId + "/slots/" + slotId, SlotDto.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().id()).isEqualTo(slotId);

        // Wrong slot ID -> 404
        ResponseEntity<ErrorResponse> notFound = restTemplate.getForEntity("/users/" + userId + "/slots/" + UUID.randomUUID(), ErrorResponse.class);
        assertThat(notFound.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("PATCH /users/{userId}/slots/{slotId} updates FREE slot and rejects BUSY slots")
    void updateSlotSuccessfullyAndRejectBusy() {
        CreateSlotRequest slotReq = new CreateSlotRequest(
                Instant.parse("2026-09-01T09:00:00Z"),
                Instant.parse("2026-09-01T10:00:00Z")
        );
        ResponseEntity<SlotDto> created = restTemplate.postForEntity("/users/" + userId + "/slots", slotReq, SlotDto.class);
        UUID slotId = created.getBody().id();

        UpdateSlotRequest updateReq = new UpdateSlotRequest(
                Instant.parse("2026-09-01T09:30:00Z"),
                Instant.parse("2026-09-01T10:30:00Z")
        );

        ResponseEntity<SlotDto> updated = restTemplate.exchange(
                "/users/" + userId + "/slots/" + slotId,
                HttpMethod.PATCH,
                new HttpEntity<>(updateReq),
                SlotDto.class
        );

        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().startTime()).isEqualTo(Instant.parse("2026-09-01T09:30:00Z"));
        assertThat(updated.getBody().endTime()).isEqualTo(Instant.parse("2026-09-01T10:30:00Z"));

        // Mark slot as BUSY in DB
        TimeSlotEntity entity = timeSlotRepository.findById(slotId).orElseThrow();
        entity.setStatus(SlotStatus.BUSY);
        timeSlotRepository.save(entity);

        // Attempting to patch BUSY slot -> 409
        ResponseEntity<ErrorResponse> conflict = restTemplate.exchange(
                "/users/" + userId + "/slots/" + slotId,
                HttpMethod.PATCH,
                new HttpEntity<>(updateReq),
                ErrorResponse.class
        );
        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(conflict.getBody().code()).isEqualTo("SLOT_NOT_FREE");
    }

    @Test
    @DisplayName("DELETE /users/{userId}/slots/{slotId} deletes FREE slot and rejects BUSY slot")
    void deleteSlotSuccessfullyAndRejectBusy() {
        CreateSlotRequest slotReq = new CreateSlotRequest(
                Instant.parse("2026-09-01T09:00:00Z"),
                Instant.parse("2026-09-01T10:00:00Z")
        );
        ResponseEntity<SlotDto> created = restTemplate.postForEntity("/users/" + userId + "/slots", slotReq, SlotDto.class);
        UUID slotId = created.getBody().id();

        ResponseEntity<Void> deleted = restTemplate.exchange(
                "/users/" + userId + "/slots/" + slotId,
                HttpMethod.DELETE,
                null,
                Void.class
        );
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(timeSlotRepository.findById(slotId)).isEmpty();

        // Create another slot and mark BUSY
        ResponseEntity<SlotDto> busyCreated = restTemplate.postForEntity("/users/" + userId + "/slots", slotReq, SlotDto.class);
        UUID busySlotId = busyCreated.getBody().id();
        TimeSlotEntity entity = timeSlotRepository.findById(busySlotId).orElseThrow();
        entity.setStatus(SlotStatus.BUSY);
        timeSlotRepository.save(entity);

        ResponseEntity<ErrorResponse> conflict = restTemplate.exchange(
                "/users/" + userId + "/slots/" + busySlotId,
                HttpMethod.DELETE,
                null,
                ErrorResponse.class
        );
        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(conflict.getBody().code()).isEqualTo("SLOT_NOT_FREE");
    }
}
