# Mini Doodle — Implementation Plan

> **Purpose:** Turn the [specification](spec.md) into a sequence of implementation slices.  
> **Approach:** Spec-driven development (SDD). Each slice = SPEC → ACCEPTANCE TEST → IMPLEMENTATION → PASS.  
> **Agent Protocol:** This file is agent-agnostic. Any coding agent may pick up the next unchecked task. Update checkboxes as you complete work.  

---

## Legend

- [ ] — Not started  
- [-] — In progress  
- [x] — Complete  

---

## Phase 0: Prerequisites (read-only)

These are already done. Do not re-do.

- [x] **P0.1** — Specification written (`spec.md` v1.1)
- [x] **P0.2** — This implementation plan created (`implementation_plan.md`)
- [x] **P0.3** — Technology stack decided: Java 21+, Spring Boot 3.x, Maven, PostgreSQL 16, Flyway, Testcontainers, Micrometer/Prometheus

---

## Phase 1: Bootstrap the Application

> **Goal:** A runnable Spring Boot application that starts and connects to PostgreSQL.  
> **Prerequisites:** Phase 0.  
> **Estimated effort:** Small.  

### Tasks

- [x] **1.1** — Initialize Maven project with Spring Boot starter (Web, Data JPA, Validation, Actuator, Lombok or records)
- [x] **1.2** — Add `docker-compose.yml` with two services:
  - `postgres` (PostgreSQL 16, healthcheck)
  - `doodle-api` (Spring Boot app, depends on postgres)
- [x] **1.3** — Add `application.yml` / `application.properties` with:
  - PostgreSQL connection (host, port, db, user, pass)
  - JPA/Hibernate settings (ddl-auto = validate — schema managed by Flyway)
  - Basic logging
- [x] **1.4** — Add Flyway dependency and create `db/migration` directory
- [x] **1.5** — Add Testcontainers dependency + JUnit 5 integration test setup
  - Create a base integration test class that spins up PostgreSQL via Testcontainers
  - Verify the context loads and the DB is reachable
- [x] **1.6** — Add a simple health endpoint (`GET /actuator/health`) and verify it responds `200 OK`
- [x] **1.7** — Add `.gitignore`, Maven wrapper, and verify `docker compose up` works end-to-end
- [x] **1.8** — Add basic Micrometer/Prometheus metrics dependency (for Phase 5)

### Acceptance Criteria

- [x] `mvn clean verify` passes (at minimum, the context-load test)
- [x] `docker compose up` starts both services successfully
- [x] `GET http://localhost:8080/actuator/health` returns `{"status":"UP"}`
- [x] Integration test base class is reusable for future tests

### Commit

```text
chore: bootstrap Spring Boot application
```

---

## Phase 2: Implement the Database Schema

> **Goal:** All tables, constraints, and indexes are in place via Flyway migration.  
> **Prerequisites:** Phase 1 complete.  
> **Estimated effort:** Small.  
> **Spec references:** §23 Persistence Specification, §24 Transactional Requirements  

### Tasks

- [x] **2.1** — Create `V1__init_schema.sql` with:
  - `users` table (UUID PK, name, email UNIQUE, created_at, updated_at)
  - `time_slots` table (UUID PK, user_id FK, start_time TIMESTAMPTZ, end_time TIMESTAMPTZ, status, created_at, updated_at)
  - `meetings` table (UUID PK, slot_id FK UNIQUE, title, description, created_at, updated_at)
  - `meeting_participants` table (meeting_id FK, user_id FK, composite PK)
- [x] **2.2** — Add `CHECK (start_time < end_time)` on `time_slots`
- [x] **2.3** — Add exclusion constraint on `time_slots`:
  ```sql
  EXCLUDE USING gist (
      user_id WITH =,
      tstzrange(start_time, end_time, '[)') WITH &&
  )
  ```
- [x] **2.4** — Add index on `time_slots(user_id, start_time, end_time)`
- [x] **2.5** — Add `UNIQUE(slot_id)` on `meetings`
- [x] **2.6** — Write integration test that:
  - Runs the migration
  - Verifies all tables exist
  - Verifies the exclusion constraint rejects overlapping slots for the same user
  - Verifies the exclusion constraint allows adjacent slots for the same user
  - Verifies `UNIQUE(slot_id)` rejects duplicate meetings for the same slot
  - Verifies `UNIQUE(email)` rejects duplicate emails

### Acceptance Criteria

- [x] Flyway migration runs cleanly on `docker compose up`
- [x] Integration tests pass and cover all constraints listed above
- [x] Schema matches §23 exactly

### Commit

```text
feat: add scheduling database schema
```

---

## Phase 3: Domain + Persistence Layer

> **Goal:** Clean domain model + JPA repositories. No controllers yet.  
> **Prerequisites:** Phase 2 complete.  
> **Estimated effort:** Medium.  
> **Spec references:** §4 Domain Model, §23 Persistence Specification  

### Tasks

- [ ] **3.1** — Define package structure:
  ```text
  com.example.doodle
  ├── user
  │   ├── domain (User)
  │   ├── persistence (UserEntity, UserRepository)
  │   └── application (UserService, UserDto, UserMapper)
  ├── slot
  │   ├── domain (TimeSlot, SlotStatus enum)
  │   ├── persistence (TimeSlotEntity, TimeSlotRepository)
  │   └── application (TimeSlotService, SlotDto, SlotMapper)
  ├── meeting
  │   ├── domain (Meeting, MeetingParticipant)
  │   ├── persistence (MeetingEntity, MeetingParticipantEntity, MeetingRepository)
  │   └── application (MeetingService, MeetingDto, MeetingMapper)
  └── availability
      ├── domain (AvailabilityPeriod)
      └── application (AvailabilityService)
  ```
- [ ] **3.2** — Implement `User` domain object + `UserEntity` JPA entity + `UserRepository`
  - Use UUID for ID (generate in app layer)
  - Use `Instant` for timestamps
  - Map `created_at` / `updated_at` with `@CreationTimestamp` / `@UpdateTimestamp` or explicit setters
- [ ] **3.3** — Implement `TimeSlot` domain object + `TimeSlotEntity` + `TimeSlotRepository`
  - `SlotStatus` enum: `FREE`, `BUSY`
  - Map `TIMESTAMPTZ` ↔ `Instant`
  - Repository query: `findByUserIdAndTimeRange(userId, from, to, status)` — filter by overlap + optional status
- [ ] **3.4** — Implement `Meeting` domain object + `MeetingEntity` + `MeetingRepository`
  - One-to-one with `TimeSlot` via `slot_id`
- [ ] **3.5** — Implement `MeetingParticipant` domain + `MeetingParticipantEntity`
  - Many-to-many link table with composite PK
- [ ] **3.6** — Write integration tests for repositories:
  - Save and retrieve each entity
  - Query slots by user + time range
  - Verify JPA mappings are correct
  - Verify `Instant` round-trips correctly through PostgreSQL `TIMESTAMPTZ`

### Acceptance Criteria

- [ ] All repositories have integration tests that pass
- [ ] Domain objects are separate from JPA entities (or at minimum, the domain is not polluted by framework annotations)
- [ ] `TimeSlotRepository` can query overlapping slots correctly
- [ ] `UserRepository` enforces email uniqueness at the DB level

### Commit

```text
feat: add domain model and persistence layer
```

---

## Phase 4: Vertical Feature Slices

> **Goal:** Implement features end-to-end (controller → service → repository → test) one at a time.  
> **Prerequisites:** Phase 3 complete.  
> **Approach:** Each slice = write the acceptance test first, then implement.  

---

### Slice 4.1: User Management

> **Spec references:** §18 Users  

- [ ] **4.1.1** — Write acceptance test: `POST /users` creates a user and returns `201`
- [ ] **4.1.2** — Implement `POST /users` endpoint
  - Validate name and email are present
  - Return `201 Created` with user JSON
- [ ] **4.1.3** — Write acceptance test: `GET /users/{userId}` returns `200` for existing user, `404` for missing user
- [ ] **4.1.4** — Implement `GET /users/{userId}` endpoint
- [ ] **4.1.5** — Run all tests; ensure they pass

**Acceptance Criteria:**
- [ ] `POST /users` with valid body returns `201` + user with UUID id
- [ ] `POST /users` with duplicate email returns `409 Conflict` (or `400` — pick one, document it)
- [ ] `GET /users/{id}` returns `200` for existing, `404` for non-existing

**Commit:**
```text
feat: implement user CRUD endpoints
```

---

### Slice 4.2: Create Time Slot

> **Spec references:** §5 FR-1, §16 INV-1, INV-2  

- [ ] **4.2.1** — Write acceptance test: `POST /users/{userId}/slots` creates a FREE slot
- [ ] **4.2.2** — Implement `POST /users/{userId}/slots`
  - Validate `startTime < endTime`
  - Validate user exists (404 if not)
  - Let the DB exclusion constraint catch overlaps
  - Return `201 Created` with slot JSON (status = `FREE`)
  - Return `409 Conflict` if exclusion constraint violated (overlapping slot)
  - Return `400 Bad Request` if `startTime >= endTime`
- [ ] **4.2.3** — Write integration test: concurrent slot creation for same user with overlapping times — only one succeeds
- [ ] **4.2.4** — Run all tests; ensure they pass

**Acceptance Criteria:**
- [ ] Valid slot → `201`, status = `FREE`
- [ ] `startTime >= endTime` → `400`
- [ ] Overlapping slot for same user → `409`
- [ ] Adjacent slot for same user (`09:00-10:00` then `10:00-11:00`) → `201` (allowed)
- [ ] Slot for non-existent user → `404`

**Commit:**
```text
feat: implement create time slot endpoint
```

---

### Slice 4.3: List / Get / Update / Delete Time Slots

> **Spec references:** §6 FR-2, §7 FR-3, §8 FR-4, §9 FR-5  

#### List Slots
- [ ] **4.3.1** — Write acceptance test: `GET /users/{userId}/slots?from=...&to=...` returns slots overlapping the range
- [ ] **4.3.2** — Implement list endpoint with optional `from`, `to`, `status` query params
  - Use overlap semantics: `slot.start < to AND slot.end > from`
  - Clip partial overlaps in the response
- [ ] **4.3.3** — Write test for clipping behavior (e.g., slot `09:00-11:00` queried with `10:00-12:00` returns `10:00-11:00`)

#### Get Single Slot
- [ ] **4.3.4** — Write acceptance test: `GET /users/{userId}/slots/{slotId}` returns `200` or `404`
- [ ] **4.3.5** — Implement get single slot endpoint

#### Update Slot
- [ ] **4.3.6** — Write acceptance test: `PATCH /users/{userId}/slots/{slotId}` updates a FREE slot
- [ ] **4.3.7** — Implement update endpoint
  - Slot must exist and belong to user → `404` otherwise
  - Slot must be `FREE` → `409` if `BUSY`
  - New time range must be valid → `400` otherwise
  - New time range must not overlap another slot → `409` otherwise
  - Let DB exclusion constraint enforce the overlap rule

#### Delete Slot
- [ ] **4.3.8** — Write acceptance test: `DELETE /users/{userId}/slots/{slotId}` deletes a FREE slot
- [ ] **4.3.9** — Implement delete endpoint
  - Slot must exist and belong to user → `404` otherwise
  - Slot must be `FREE` → `409` if `BUSY`
  - Return `204 No Content`
- [ ] **4.3.10** — Run all tests; ensure they pass

**Acceptance Criteria:**
- [ ] List returns only slots overlapping the requested timeframe
- [ ] Partial overlaps are clipped in the response
- [ ] Get returns `404` for wrong user or missing slot
- [ ] Update works only on FREE slots; rejects overlaps
- [ ] Delete works only on FREE slots
- [ ] All edge cases covered by tests

**Commit:**
```text
feat: implement list, get, update, delete slot endpoints
```

---

### Slice 4.4: Book a Meeting (The Critical Slice)

> **Spec references:** §10 FR-6, §11 FR-7, §16 INV-3 through INV-12, §24 Transactional Requirements  
> **This is the most important slice. Take your time.**  

- [ ] **4.4.1** — Write acceptance test: `POST /users/{userId}/slots/{slotId}/meeting` books a meeting
  - Setup: create owner user, create 2+ participant users, create a FREE slot for owner
  - Request: title, description, participantIds (including owner + at least one other)
  - Assert: `201 Created`, slot status becomes `BUSY`, meeting exists, participants are linked
- [ ] **4.4.2** — Implement booking service with **transactional atomicity**:
  ```text
  @Transactional
  bookMeeting(slotId, ownerId, request):
      1. SELECT slot FOR UPDATE
      2. Verify slot exists and belongs to owner → 404
      3. Verify slot is FREE → 409 if BUSY
      4. Validate participantIds:
         - size >= 2
         - ownerId ∈ participantIds
         - no duplicates
         - all users exist
      5. INSERT meeting
      6. INSERT meeting_participants
      7. UPDATE slot SET status = BUSY
      8. COMMIT (implicit via @Transactional)
  ```
- [ ] **4.4.3** — Implement `POST /users/{userId}/slots/{slotId}/meeting` controller
- [ ] **4.4.4** — Write negative acceptance tests:
  - Booking a `BUSY` slot → `409 Conflict`
  - Booking with non-existent participant → `400 Bad Request`
  - Booking without owner in participants → `400`
  - Booking with only owner → `400`
  - Booking with duplicate participant IDs → `400`
  - Booking for non-existent slot → `404`
  - Booking for slot belonging to another user → `404`
- [ ] **4.4.5** — Write **concurrency test**:
  - Create a FREE slot
  - Fire 10 concurrent booking requests for the same slot
  - Assert: exactly 1 returns `201`, exactly 9 return `409`
  - Assert: database contains exactly 1 meeting, 1 BUSY slot
- [ ] **4.4.6** — Run all tests; ensure they pass

**Acceptance Criteria:**
- [ ] Successful booking: `201`, slot → `BUSY`, meeting + participants persisted
- [ ] Booking a BUSY slot: `409`
- [ ] Invalid participant rules: `400` with clear error
- [ ] Concurrent booking: exactly 1 winner, 9 losers
- [ ] No partial state possible (transaction rolls back on any failure)

**Commit:**
```text
feat: implement meeting booking with concurrency safety
```

---

### Slice 4.5: Retrieve a Meeting

> **Spec references:** §12 FR-8  

- [ ] **4.5.1** — Write acceptance test: `GET /users/{userId}/meetings/{meetingId}` returns meeting details
- [ ] **4.5.2** — Implement get meeting endpoint
  - Return: meeting id, title, description, time slot (clipped? or full? — use full slot boundaries), participants list
  - Return `404` if meeting doesn't exist or doesn't belong to the user's calendar
- [ ] **4.5.3** — Run all tests; ensure they pass

**Acceptance Criteria:**
- [ ] `200 OK` with full meeting details for valid request
- [ ] `404` for non-existent meeting or wrong user

**Commit:**
```text
feat: implement get meeting endpoint
```

---

### Slice 4.6: Availability Query

> **Spec references:** §13 FR-9, §14 FR-10, §15 Availability Aggregation  

- [ ] **4.6.1** — Write acceptance test: `GET /users/{userId}/availability?from=...&to=...` returns aggregated periods
- [ ] **4.6.2** — Implement availability service:
  - Query slots from DB filtered by user + time range + optional status
  - Clip each slot to the requested timeframe
  - Sort by start time
  - Aggregate adjacent same-status periods (endTime of A == startTime of B)
  - Return list of `{startTime, endTime, status}`
- [ ] **4.6.3** — Implement `GET /users/{userId}/availability` controller
  - `from` and `to` are required query params
  - `status` is optional (`FREE` or `BUSY`)
- [ ] **4.6.4** — Write tests for aggregation logic:
  - Adjacent FREE slots merge
  - Adjacent BUSY slots merge
  - Gaps are preserved
  - Partial overlaps are clipped
  - Filtering by status works
- [ ] **4.6.5** — Run all tests; ensure they pass

**Acceptance Criteria:**
- [ ] Returns aggregated periods for the requested timeframe
- [ ] Partial overlaps clipped correctly
- [ ] Adjacent same-status periods merged
- [ ] Gaps preserved
- [ ] Optional status filter works
- [ ] Missing `from` or `to` → `400`

**Commit:**
```text
feat: implement availability query with aggregation
```

---

## Phase 5: Observability & Polish

> **Goal:** Metrics, error handling, documentation.  
> **Prerequisites:** Phase 4 complete.  
> **Estimated effort:** Small.  

### Tasks

- [ ] **5.1** — Add Micrometer counters:
  - `meeting_booking_success_total`
  - `meeting_booking_conflict_total`
  - `availability_query_total`
  - `http_requests_total` (provided by Spring Boot Actuator + Micrometer)
- [ ] **5.2** — Verify `/actuator/prometheus` exposes metrics
- [ ] **5.3** — Add global exception handler (`@ControllerAdvice`)
  - Consistent error JSON: `{ "code": "...", "message": "..." }`
  - Map domain exceptions to correct HTTP status codes per §22
- [ ] **5.4** — Write `README.md` covering:
  1. What the service does
  2. Architecture overview
  3. Technology choices
  4. How to run (`docker compose up`)
  5. How to run tests (`mvn clean verify`)
  6. API documentation (endpoints + examples)
  7. Important design decisions (exclusion constraint, SELECT FOR UPDATE, etc.)
  8. Known limitations
  9. Potential future improvements
- [ ] **5.5** — Add example `curl` commands or an `api-examples.http` file for easy testing
- [ ] **5.6** — Final review: ensure all §31 Definition of Done checkboxes can be ticked
- [ ] **5.7** — Run full test suite: `mvn clean verify`
- [ ] **5.8** — Run `docker compose up` and manually smoke-test all endpoints

### Acceptance Criteria

- [ ] `/actuator/prometheus` returns booking metrics
- [ ] All errors return consistent JSON structure
- [ ] README is complete and accurate
- [ ] Full test suite passes
- [ ] Manual smoke test passes

### Commit(s)

```text
feat: add metrics and global error handling
docs: add README with setup and API examples
```

---

## Phase 6: Final Verification

> **Goal:** Ensure nothing was missed.  

### Definition of Done Checklist

Cross-reference with [spec.md §31](spec.md#31-definition-of-done):

- [ ] **DOD.1** — Service implemented with Java and Spring Boot
- [ ] **DOD.2** — PostgreSQL used for persistence
- [ ] **DOD.3** — Users can be created and retrieved
- [ ] **DOD.4** — Users can create time slots
- [ ] **DOD.5** — Users can list time slots
- [ ] **DOD.6** — Users can update free time slots
- [ ] **DOD.7** — Users can delete free time slots
- [ ] **DOD.8** — Users can book meetings
- [ ] **DOD.9** — Meetings contain title and description
- [ ] **DOD.10** — Meetings contain participants
- [ ] **DOD.11** — Booked slots become `BUSY`
- [ ] **DOD.12** — Busy slots cannot be booked again
- [ ] **DOD.13** — Concurrent booking attempts cannot double-book a slot
- [ ] **DOD.14** — Availability can be queried for a timeframe
- [ ] **DOD.15** — Availability can be filtered by free/busy status
- [ ] **DOD.16** — Adjacent availability periods are aggregated
- [ ] **DOD.17** — Data survives application restarts
- [ ] **DOD.18** — Automated tests cover core business rules
- [ ] **DOD.19** — Integration tests verify PostgreSQL behavior
- [ ] **DOD.20** — Application runs through Docker Compose
- [ ] **DOD.21** — README documents setup and API consumption
- [ ] **DOD.22** — Meaningful Git commits are used
- [ ] **DOD.23** — Basic metrics are exposed

---

## Agent Handoff Notes

> **For the next coding agent:**  
> 1. Check which phases/slices are already complete by looking at the checkboxes above.  
> 2. Start with the first unchecked task.  
> 3. Each slice is self-contained — you only need the domain model from previous slices.  
> 4. Update checkboxes as you complete work.  
> 5. Run `mvn clean verify` before declaring a slice done.  
> 6. If you discover a spec inconsistency, document it in the README or as a code comment, then proceed with a reasonable interpretation.  
> 7. Do not skip tests. The acceptance test for each slice must pass before the slice is considered complete.  
