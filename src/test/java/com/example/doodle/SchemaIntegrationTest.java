package com.example.doodle;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SchemaIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM meeting_participants");
        jdbcTemplate.execute("DELETE FROM meetings");
        jdbcTemplate.execute("DELETE FROM time_slots");
        jdbcTemplate.execute("DELETE FROM users");
    }

    @Test
    @DisplayName("Verify all expected tables exist in schema")
    void tablesExist() {
        List<String> tableNames = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
                String.class
        );

        assertThat(tableNames).contains(
                "users",
                "time_slots",
                "meetings",
                "meeting_participants",
                "flyway_schema_history"
        );
    }

    @Test
    @DisplayName("Verify UNIQUE(email) constraint on users rejects duplicate emails")
    void duplicateEmailRejected() {
        UUID user1Id = UUID.randomUUID();
        UUID user2Id = UUID.randomUUID();
        Instant now = Instant.now();

        jdbcTemplate.update(
                "INSERT INTO users (id, name, email, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                user1Id, "Alice", "alice@example.com", Timestamp.from(now), Timestamp.from(now)
        );

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO users (id, name, email, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                user2Id, "Alice 2", "alice@example.com", Timestamp.from(now), Timestamp.from(now)
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Verify exclusion constraint rejects overlapping slots for the same user")
    void overlappingSlotsRejectedForSameUser() {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();

        jdbcTemplate.update(
                "INSERT INTO users (id, name, email, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                userId, "Bob", "bob@example.com", Timestamp.from(now), Timestamp.from(now)
        );

        Instant start1 = Instant.parse("2026-09-01T09:00:00Z");
        Instant end1 = Instant.parse("2026-09-01T10:00:00Z");

        jdbcTemplate.update(
                "INSERT INTO time_slots (id, user_id, start_time, end_time, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), userId, Timestamp.from(start1), Timestamp.from(end1), "FREE", Timestamp.from(now), Timestamp.from(now)
        );

        // Overlapping slot: 09:30 - 10:30
        Instant start2 = Instant.parse("2026-09-01T09:30:00Z");
        Instant end2 = Instant.parse("2026-09-01T10:30:00Z");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO time_slots (id, user_id, start_time, end_time, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), userId, Timestamp.from(start2), Timestamp.from(end2), "FREE", Timestamp.from(now), Timestamp.from(now)
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Verify exclusion constraint allows adjacent slots for the same user")
    void adjacentSlotsAllowedForSameUser() {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();

        jdbcTemplate.update(
                "INSERT INTO users (id, name, email, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                userId, "Charlie", "charlie@example.com", Timestamp.from(now), Timestamp.from(now)
        );

        Instant start1 = Instant.parse("2026-09-01T09:00:00Z");
        Instant end1 = Instant.parse("2026-09-01T10:00:00Z");

        jdbcTemplate.update(
                "INSERT INTO time_slots (id, user_id, start_time, end_time, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), userId, Timestamp.from(start1), Timestamp.from(end1), "FREE", Timestamp.from(now), Timestamp.from(now)
        );

        // Adjacent slot: 10:00 - 11:00
        Instant start2 = Instant.parse("2026-09-01T10:00:00Z");
        Instant end2 = Instant.parse("2026-09-01T11:00:00Z");

        int rows = jdbcTemplate.update(
                "INSERT INTO time_slots (id, user_id, start_time, end_time, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), userId, Timestamp.from(start2), Timestamp.from(end2), "FREE", Timestamp.from(now), Timestamp.from(now)
        );

        assertThat(rows).isEqualTo(1);
    }

    @Test
    @DisplayName("Verify overlapping slots are allowed for different users")
    void overlappingSlotsAllowedForDifferentUsers() {
        UUID user1Id = UUID.randomUUID();
        UUID user2Id = UUID.randomUUID();
        Instant now = Instant.now();

        jdbcTemplate.update(
                "INSERT INTO users (id, name, email, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                user1Id, "User 1", "user1@example.com", Timestamp.from(now), Timestamp.from(now)
        );
        jdbcTemplate.update(
                "INSERT INTO users (id, name, email, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                user2Id, "User 2", "user2@example.com", Timestamp.from(now), Timestamp.from(now)
        );

        Instant start = Instant.parse("2026-09-01T09:00:00Z");
        Instant end = Instant.parse("2026-09-01T10:00:00Z");

        jdbcTemplate.update(
                "INSERT INTO time_slots (id, user_id, start_time, end_time, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), user1Id, Timestamp.from(start), Timestamp.from(end), "FREE", Timestamp.from(now), Timestamp.from(now)
        );

        int rows = jdbcTemplate.update(
                "INSERT INTO time_slots (id, user_id, start_time, end_time, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), user2Id, Timestamp.from(start), Timestamp.from(end), "FREE", Timestamp.from(now), Timestamp.from(now)
        );

        assertThat(rows).isEqualTo(1);
    }

    @Test
    @DisplayName("Verify CHECK (start_time < end_time) constraint rejects start >= end")
    void invalidTimeRangeRejected() {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();

        jdbcTemplate.update(
                "INSERT INTO users (id, name, email, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                userId, "Dave", "dave@example.com", Timestamp.from(now), Timestamp.from(now)
        );

        Instant start = Instant.parse("2026-09-01T10:00:00Z");
        Instant end = Instant.parse("2026-09-01T09:00:00Z");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO time_slots (id, user_id, start_time, end_time, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), userId, Timestamp.from(start), Timestamp.from(end), "FREE", Timestamp.from(now), Timestamp.from(now)
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Verify UNIQUE(slot_id) constraint on meetings rejects duplicate meetings for same slot")
    void duplicateMeetingForSameSlotRejected() {
        UUID userId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();
        Instant now = Instant.now();

        jdbcTemplate.update(
                "INSERT INTO users (id, name, email, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                userId, "Eve", "eve@example.com", Timestamp.from(now), Timestamp.from(now)
        );

        Instant start = Instant.parse("2026-09-01T09:00:00Z");
        Instant end = Instant.parse("2026-09-01T10:00:00Z");

        jdbcTemplate.update(
                "INSERT INTO time_slots (id, user_id, start_time, end_time, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                slotId, userId, Timestamp.from(start), Timestamp.from(end), "BUSY", Timestamp.from(now), Timestamp.from(now)
        );

        jdbcTemplate.update(
                "INSERT INTO meetings (id, slot_id, title, description, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), slotId, "Meeting 1", "Description 1", Timestamp.from(now), Timestamp.from(now)
        );

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO meetings (id, slot_id, title, description, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), slotId, "Meeting 2", "Description 2", Timestamp.from(now), Timestamp.from(now)
        )).isInstanceOf(DataIntegrityViolationException.class);
    }
}
