# Mini Doodle Backend Specification

**Version:** 1.1
**Status:** Draft
**Technology:** Java / Spring Boot / PostgreSQL

## 1. Purpose

The goal of this project is to implement a mini meeting-scheduling platform inspired by Doodle.

The service allows users to:

* Manage their available time slots.
* Schedule meetings using available slots.
* Query their free and busy availability over a selected timeframe.
* Persist all scheduling data.

The original challenge expects the system to support hundreds of users and thousands of slots, and emphasizes design and technical decision-making. It also requires the application to run locally using Docker Compose and asks for clear consumption documentation, with tests and metrics considered a plus.

---

# 2. Goals

The system must provide:

1. Persistent user management.
2. Persistent time-slot management.
3. Meeting scheduling.
4. Free/busy availability queries.
5. Protection against conflicting bookings.
6. Correct behavior under concurrent booking attempts.
7. A simple, documented HTTP API.
8. Local execution using Docker Compose.
9. Automated tests covering important business rules.

---

# 3. Non-Goals

The following are intentionally out of scope for version 1:

* Authentication and authorization.
* Email notifications.
* Calendar synchronization with external providers.
* Recurring meetings.
* Meeting reminders.
* Meeting cancellation/rescheduling.
* Frontend/UI.
* Video conferencing.
* Time-zone conversion.
* Distributed services/microservices.
* Message queues.
* Caching.
* Kubernetes deployment.

These features may be discussed as future improvements but should not complicate the initial implementation.

---

# 4. Domain Model

The core domain consists of:

* `User`
* `Calendar`
* `TimeSlot`
* `Meeting`
* `MeetingParticipant`

The challenge specifies that **Calendar is a domain concept only**. The system does not need to expose a separate Calendar CRUD API.

## 4.1 User

A user represents a person who owns availability and can participate in meetings.

### Attributes

```text
id
name
email
createdAt
updatedAt
```

A user owns one logical calendar.

---

## 4.2 Calendar

A calendar represents a user's personal scheduling context.

For version 1, Calendar does not require its own persistence model or public API.

Conceptually:

```text
User
  |
  └── Calendar
        |
        ├── TimeSlot
        ├── TimeSlot
        └── TimeSlot
```

---

## 4.3 TimeSlot

A time slot represents a period of time belonging to a user's calendar.

### Attributes

```text
id
userId
startTime
endTime
status
createdAt
updatedAt
```

### Status

```text
FREE
BUSY
```

A newly created slot is always `FREE`.

A slot becomes `BUSY` when a meeting is successfully scheduled against it.

---

## 4.4 Meeting

A meeting represents a scheduled meeting associated with exactly one time slot.

### Attributes

```text
id
slotId
title
description
createdAt
updatedAt
```

A time slot can have zero or one meeting.

```text
TimeSlot 1 ───── 0..1 Meeting
```

---

## 4.5 MeetingParticipant

A meeting participant represents a user participating in a meeting.

### Attributes

```text
meetingId
userId
```

A participant may participate in multiple meetings.

---

# 5. Functional Requirements

## FR-1 — Create a Time Slot

A user must be able to create an available time slot.

### Input

```json
{
  "startTime": "2026-09-01T09:00:00Z",
  "endTime": "2026-09-01T10:00:00Z"
}
```

### Behavior

A successful request creates a slot with:

```text
status = FREE
```

### Validation

* `startTime` is required.
* `endTime` is required.
* `startTime` must be before `endTime`.
* The slot must not overlap an existing slot belonging to the same user.

### Acceptance Criteria

* A valid slot is persisted.
* The returned slot has status `FREE`.
* Invalid time ranges are rejected.
* Overlapping slots are rejected.

---

# 6. FR-2 — Retrieve Time Slots

A user must be able to retrieve their time slots.

The API must support filtering by timeframe.

Example:

```http
GET /users/{userId}/slots?from=2026-09-01T00:00:00Z&to=2026-09-02T00:00:00Z
```

A slot is relevant to a requested timeframe when it overlaps the requested interval.

The overlap uses the interval semantics:

```text
slot.startTime < requested.to
AND
slot.endTime > requested.from
```

When a slot only partially overlaps the requested timeframe, the returned availability/slot representation must be clipped to the requested timeframe.

For example, a requested timeframe of `10:00-12:00` and a slot of `09:00-11:00` produces:

```text
10:00-11:00
```

A slot ending exactly at `requested.from` or starting exactly at `requested.to` does not overlap the requested timeframe.

---

# 7. FR-3 — Retrieve a Single Time Slot

A user must be able to retrieve a specific time slot.

Example:

```http
GET /users/{userId}/slots/{slotId}
```

If the slot does not exist or does not belong to the requested user, the service returns `404 Not Found`.

---

# 8. FR-4 — Update a Time Slot

A user may modify an existing `FREE` time slot.

Example:

```http
PATCH /users/{userId}/slots/{slotId}
```

```json
{
  "startTime": "2026-09-01T09:30:00Z",
  "endTime": "2026-09-01T10:30:00Z"
}
```

### Rules

* The slot must exist.
* The slot must belong to the user.
* The slot must currently be `FREE`.
* The new time range must be valid.
* The new time range must not overlap another slot belonging to the same user.

A `BUSY` slot cannot be modified.

---

# 9. FR-5 — Delete a Time Slot

A user may delete an existing `FREE` time slot.

Example:

```http
DELETE /users/{userId}/slots/{slotId}
```

### Rules

* The slot must exist.
* The slot must belong to the user.
* The slot must be `FREE`.

A slot containing a meeting cannot be deleted.

---

# 10. FR-6 — Schedule a Meeting

A user must be able to convert an available slot into a meeting.

Example:

```http
POST /users/{userId}/slots/{slotId}/meeting
```

Request:

```json
{
  "title": "Architecture Discussion",
  "description": "Discuss backend architecture",
  "participantIds": [
    "user-2",
    "user-3"
  ]
}
```

### Behavior

A successful operation must:

1. Verify that the slot exists.
2. Verify that the slot belongs to the user.
3. Verify that the slot is `FREE`.
4. Create the meeting.
5. Associate the participants.
6. Change the slot status to `BUSY`.

Participant rules:

- Every `participantId` must reference an existing user.
- The slot owner must be included in `participantIds`.
- The slot owner cannot be the only participant.
- Therefore, a meeting must contain at least two participants, including the slot owner.
- Duplicate participant IDs are not allowed.

The entire operation must be atomic.

Conceptually:

```text
FREE SLOT
   |
   | book
   v
BUSY SLOT + MEETING
```

If any part of the operation fails, no partial state may be persisted.

---

# 11. FR-7 — Prevent Double Booking

A time slot may only be booked once.

This requirement must hold even when multiple requests attempt to book the same slot concurrently.

Example:

```text
Request A ─────┐
               ├──> Slot 123
Request B ─────┘
```

If both requests arrive concurrently:

```text
Exactly one request → SUCCESS
All other requests  → CONFLICT
```

The system must never create two meetings for the same slot.

This is a core correctness requirement.

---

# 12. FR-8 — Retrieve a Meeting

A user must be able to retrieve a meeting associated with their calendar.

Example:

```http
GET /users/{userId}/meetings/{meetingId}
```

The response should contain:

* Meeting ID
* Title
* Description
* Time slot
* Participants

---

# 13. FR-9 — Query Availability

A user must be able to query their availability for a selected timeframe.

Example:

```http
GET /users/{userId}/availability?from=2026-09-01T09:00:00Z&to=2026-09-01T18:00:00Z
```

The response must expose the user's availability as `FREE` and `BUSY` periods.

The challenge explicitly requires querying free or busy slots with an aggregated view for a selected timeframe.

---

# 14. FR-10 — Availability Filtering

The availability endpoint should support an optional status filter.

```text
status=FREE
status=BUSY
```

If no status is provided, both states are returned.

Example:

```http
GET /users/{userId}/availability
    ?from=2026-09-01T09:00:00Z
    &to=2026-09-01T18:00:00Z
    &status=FREE
```

---

# 15. Availability Aggregation

Adjacent periods with the same status should be aggregated.

Given:

```text
09:00 - 10:00 FREE
10:00 - 11:00 FREE
11:00 - 12:00 BUSY
12:00 - 13:00 FREE
13:00 - 14:00 FREE
```

The API should return:

```text
09:00 - 11:00 FREE
11:00 - 12:00 BUSY
12:00 - 14:00 FREE
```

This provides a more useful calendar availability representation.

Aggregation rules:

- Only adjacent periods can be merged.
- Two periods are adjacent when the first period's `endTime` exactly equals the second period's `startTime`.
- Periods must have the same status to be merged.
- Gaps are never filled or removed.
- The first and last returned periods are clipped to the requested timeframe when they partially overlap it.

---

# 16. Domain Invariants

The following invariants must always hold.

## INV-1 — Valid Time Range

```text
startTime < endTime
```

---

## INV-2 — No Overlapping Slots

For any user:

```text
slot A ∩ slot B = ∅
```

Two slots belonging to the same user must never overlap.

---

## INV-3 — Maximum One Meeting Per Slot

```text
COUNT(meetings WHERE slot_id = X) <= 1
```

---

## INV-4 — Meeting Implies Busy

```text
meeting exists
    =>
slot.status = BUSY
```

---

## INV-5 — Free Means No Meeting

```text
slot.status = FREE
    =>
no meeting exists for the slot
```

---

## INV-6 — Only Free Slots Can Be Booked

A booking request against a `BUSY` slot must fail.

---

## INV-7 — Booking Is Atomic

Creating the meeting and marking the slot `BUSY` must occur as one transaction.

There must never be a committed state where:

```text
Meeting exists
+
Slot is FREE
```

or:

```text
Slot is BUSY
+
Meeting does not exist
```

---

## INV-8 — Concurrent Booking Has One Winner

For concurrent requests against the same slot:

```text
successful bookings <= 1
```

---

## INV-9 — Participants Must Exist

Every meeting participant must reference an existing user.

---

## INV-10 — Slot Owner Must Participate

The owner of the booked slot must be included in the meeting's participant list.

```text
slot.ownerId ∈ participantIds
```

---

## INV-11 — Meeting Must Have Multiple Participants

The slot owner cannot be the only participant.

```text
COUNT(participantIds) >= 2
```

Therefore every meeting has at least two distinct users, including the slot owner.

---

## INV-12 — Participant IDs Are Unique

A meeting must not contain the same user more than once.

---

# 17. State Model

A time slot has two states:

```text
        ┌─────────────┐
        │             │
        │    FREE     │
        │             │
        └──────┬──────┘
               │
             BOOK
               │
               ▼
        ┌─────────────┐
        │             │
        │    BUSY     │
        │             │
        └─────────────┘
```

Allowed transitions:

```text
FREE  → BUSY
```

No transition back to `FREE` exists in version 1 because meeting cancellation is out of scope.

---

# 18. API Specification

## Users

### Create User

```http
POST /users
```

Request:

```json
{
  "name": "Youssef",
  "email": "youssef@example.com"
}
```

Response:

```http
201 Created
```

```json
{
  "id": "user-123",
  "name": "Youssef",
  "email": "youssef@example.com"
}
```

---

### Get User

```http
GET /users/{userId}
```

Response:

```http
200 OK
```

---

# 19. Slot API

### Create

```http
POST /users/{userId}/slots
```

### List

```http
GET /users/{userId}/slots
```

Optional query parameters:

```text
from
to
status
```

### Get

```http
GET /users/{userId}/slots/{slotId}
```

### Update

```http
PATCH /users/{userId}/slots/{slotId}
```

### Delete

```http
DELETE /users/{userId}/slots/{slotId}
```

---

# 20. Meeting API

### Create Meeting

```http
POST /users/{userId}/slots/{slotId}/meeting
```

### Get Meeting

```http
GET /users/{userId}/meetings/{meetingId}
```

---

# 21. Availability API

```http
GET /users/{userId}/availability
```

Query parameters:

```text
from      required
to        required
status    optional
```

Example:

```http
GET /users/user-123/availability
    ?from=2026-09-01T09:00:00Z
    &to=2026-09-01T18:00:00Z
```

---

# 22. Error Semantics

The API should use conventional HTTP status codes.

| Situation                                   |                 HTTP Status |
| ------------------------------------------- | --------------------------: |
| Successful creation                         |               `201 Created` |
| Successful retrieval/update                 |                    `200 OK` |
| Successful deletion                         |            `204 No Content` |
| Invalid request                             |           `400 Bad Request` |
| Resource does not exist                     |             `404 Not Found` |
| Slot already booked / conflicting operation |              `409 Conflict` |
| Unexpected server error                     | `500 Internal Server Error` |

Error responses should use a consistent structure.

Example:

```json
{
  "code": "SLOT_ALREADY_BOOKED",
  "message": "The requested time slot is already booked."
}
```

---

# 23. Persistence Specification

PostgreSQL will be used as the persistent datastore.

All scheduling state must survive service restarts.

The challenge explicitly requires persistence of all data.

## 23.1 Database Model

```text
users
  │
  ├──────────────┐
  │              │
  ▼              ▼
time_slots     meeting_participants
  │                    ▲
  │                    │
  ▼                    │
meetings ───────────────┘
```

## 23.2 Users

```sql
CREATE TABLE users (
    id UUID PRIMARY KEY,
    name VARCHAR NOT NULL,
    email VARCHAR NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
```

Constraints:

* `id` primary key
* `email` unique
* required fields non-null

## 23.3 Time Slots

```sql
CREATE TABLE time_slots (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    start_time TIMESTAMPTZ NOT NULL,
    end_time TIMESTAMPTZ NOT NULL,
    status VARCHAR NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
```

Constraints:

* `id` primary key
* `user_id` foreign key
* `start_time < end_time`
* valid status
* appropriate index on `(user_id, start_time, end_time)`

### 23.3.1 Exclusion Constraint for Overlap Prevention

To enforce INV-2 (no overlapping slots) at the database level, an exclusion constraint is used:

```sql
ALTER TABLE time_slots
ADD CONSTRAINT no_overlapping_slots
EXCLUDE USING gist (
    user_id WITH =,
    tstzrange(start_time, end_time, '[)') WITH &&
);
```

The `[)` (half-open) range semantics mean:

* `09:00-10:00` and `10:00-11:00` are allowed (adjacent, non-overlapping).
* `09:00-10:00` and `09:30-10:30` are rejected (overlapping).

This is a **database-enforced domain invariant**, not merely application logic. It prevents the classic check-then-act race condition where two concurrent requests could both verify no overlap exists and then both insert overlapping slots.

### 23.3.2 Application-Level Validation

There are two layers of defense:

```text
Application
    ↓
friendly validation / error handling
    ↓
Database
    ↓
absolute correctness guarantee
```

The application should still check for overlap and produce a useful domain exception with a clear error message. But the database remains the final authority. If two requests race, PostgreSQL still prevents the invalid state.

## 23.4 Meetings

```sql
CREATE TABLE meetings (
    id UUID PRIMARY KEY,
    slot_id UUID NOT NULL REFERENCES time_slots(id) UNIQUE,
    title VARCHAR NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
```

Constraints:

* `id` primary key
* `slot_id` foreign key
* `slot_id` unique — guarantees that a slot cannot have multiple meetings

## 23.5 Meeting Participants

```sql
CREATE TABLE meeting_participants (
    meeting_id UUID NOT NULL REFERENCES meetings(id),
    user_id UUID NOT NULL REFERENCES users(id),
    PRIMARY KEY (meeting_id, user_id)
);
```

Constraints:

* composite primary key `(meeting_id, user_id)`
* foreign key to `meetings`
* foreign key to `users`

## 23.6 Identifier Strategy: UUID

All primary keys use **UUIDs**.

Reasons:

* Good API identifiers — opaque, non-sequential.
* No information leakage through sequential IDs.
* Easy to generate in the application layer.
* No dependency on database-generated sequential identifiers.

For a challenge of this scale, UUID performance is completely reasonable.

## 23.7 Timestamp Strategy: TIMESTAMPTZ + Instant

PostgreSQL uses:

```sql
TIMESTAMPTZ
```

And Java uses:

```java
Instant
```

rather than `LocalDateTime`.

This is important even though timezone conversion is out of scope. We are representing **points in time**, so `Instant` is the right domain representation.

For example:

```text
2026-09-01T09:00:00Z
```

rather than an ambiguous:

```text
2026-09-01 09:00
```

---

# 24. Transactional Requirements

Meeting creation must execute inside a database transaction.

Conceptually:

```text
BEGIN

1. Lock / safely acquire the target slot
2. Verify slot is FREE
3. Create meeting
4. Create participants
5. Change slot to BUSY

COMMIT
```

If any operation fails:

```text
ROLLBACK
```

The exact locking strategy is an implementation decision, but the implementation must satisfy the concurrency invariants defined above.

## 24.1 Booking Concurrency Strategy

To prevent double booking under concurrent load, use `SELECT ... FOR UPDATE`:

```sql
SELECT *
FROM time_slots
WHERE id = ?
FOR UPDATE;
```

Inside a transaction:

```text
BEGIN TRANSACTION

       │
       ▼
SELECT slot
FOR UPDATE
       │
       ▼
Is slot FREE?
   │          │
  NO         YES
   │          │
   ▼          ▼
409       Validate participants
              │
              ▼
        Create meeting
              │
              ▼
        Create participants
              │
              ▼
       UPDATE time_slot
       SET status = BUSY
              │
              ▼
           COMMIT
```

The second concurrent transaction waits for the first transaction's row lock. Once the first transaction commits, the second request:

1. Acquires the lock.
2. Reads the slot as `BUSY`.
3. Returns `409 Conflict`.

## 24.2 Defense in Depth

Even with row locking, the `UNIQUE (slot_id)` constraint on `meetings` provides a final safety net. The database architecture is:

```text
Application transaction
        +
SELECT FOR UPDATE
        +
UNIQUE(slot_id)
```

The unique constraint makes it impossible for the database to contain two meetings for the same slot, regardless of application bugs.

## 24.3 Participant Validation

Before inserting the meeting, validate:

* `participantIds.size >= 2`
* `slot.ownerId ∈ participantIds`
* All `participantIds` exist in `users`
* `participantIds` are unique

Existence can be validated efficiently with:

```sql
SELECT id
FROM users
WHERE id IN (...)
```

Then compare the returned IDs with the requested IDs. There is no need to query every participant individually.

---

# 25. Query Performance

The expected workload is:

* Hundreds of users.
* Thousands of slots.

The design should therefore avoid loading all slots into application memory for availability queries.

Availability queries should be scoped by:

```text
userId
time range
optional status
```

The database should perform the initial filtering.

## 25.1 Availability Query

The overlap condition is:

```sql
start_time < :to
AND end_time > :from
```

For example, a requested timeframe of `10:00-12:00` and a slot of `09:00-11:00` matches because:

```text
09:00 < 12:00
AND
11:00 > 10:00
```

Then clip in the application layer:

```text
max(slot.start_time, requested.from)
min(slot.end_time, requested.to)
```

producing:

```text
10:00 ─── 11:00
```

## 25.2 Aggregation Strategy

Recommended approach: **database filtering + application aggregation**.

PostgreSQL performs:

```text
user + time range + status
        ↓
only relevant slots
```

Then Java performs:

```text
sort
 ↓
clip
 ↓
aggregate adjacent same-status periods
```

Why? Because aggregation is domain behavior, not merely persistence logic. At the expected scale (hundreds of users, thousands of slots), this is trivial computationally. A complicated SQL window-function solution is unnecessary.

## 25.3 Indexing

Potential index:

```text
(user_id, start_time)
```

Additional indexing decisions should be validated using the actual query plans if necessary.

---

# 26. Architecture

The service should initially be implemented as a modular monolith.

Suggested layers:

```text
HTTP / Controller
       |
       v
Application / Service
       |
       v
Domain
       |
       v
Persistence / Repository
       |
       v
PostgreSQL
```

Suggested package structure:

```text
com.example.doodle
├── user
│   ├── api
│   ├── application
│   ├── domain
│   └── persistence
│
├── slot
│   ├── api
│   ├── application
│   ├── domain
│   └── persistence
│
├── meeting
│   ├── api
│   ├── application
│   ├── domain
│   └── persistence
│
└── availability
    ├── api
    ├── application
    └── domain
```

The exact package structure may change during implementation if a simpler design proves more appropriate.

## 26.1 Resulting Architecture

```text
                    HTTP
                     │
                     ▼
              REST Controllers
                     │
                     ▼
             Application Services
              │              │
              ▼              ▼
           Domain        Transactions
              │              │
              └──────┬───────┘
                     ▼
                Repositories
                     │
                     ▼
                PostgreSQL
                     │
       ┌─────────────┴─────────────┐
       │                           │
       ▼                           ▼
 exclusion constraint          unique(slot_id)
 overlapping slots             one meeting
```

---

# 27. Testing Specification

Testing is part of the definition of done.

## Unit Tests

Business rules must be tested independently of infrastructure.

Required cases include:

* Valid slot creation.
* Invalid slot creation.
* End time before start time.
* Zero-duration slot.
* Overlapping slot.
* Non-overlapping slot.
* Updating a free slot.
* Updating a busy slot.
* Deleting a free slot.
* Deleting a busy slot.
* Booking a free slot.
* Booking a busy slot.
* Booking with a non-existent participant.
* Booking without the slot owner.
* Booking with only the slot owner.
* Booking with duplicate participant IDs.

## Integration Tests

Integration tests should verify:

* Database persistence.
* Database constraints.
* Repository queries.
* Transactions.
* Meeting creation.
* Availability queries.

PostgreSQL should be used for integration tests rather than replacing database behavior with an in-memory approximation.

## Concurrency Test

A dedicated test should send multiple concurrent booking requests for the same slot.

Example:

```text
10 concurrent requests
        |
        v
    same slot
```

Expected result:

```text
1 successful booking
9 conflicts
```

The final database state must contain:

```text
1 BUSY slot
1 meeting
```

---

# 28. Docker Specification

The complete application must be runnable locally using Docker Compose, as required by the challenge.

Expected services:

```text
docker-compose
    |
    ├── doodle-api
    |
    └── postgres
```

The README must document:

```bash
docker compose up
```

and how to consume the API.

The service should not require manually installing PostgreSQL on the host machine.

---

# 29. Documentation Requirements

The repository README must explain:

1. What the service does.
2. Architecture overview.
3. Technology choices.
4. How to run it.
5. How to run tests.
6. API documentation.
7. Example API requests.
8. Important design decisions.
9. Known limitations.
10. Potential future improvements.

The original challenge specifically requests clear documentation describing how the service can be consumed.

---

# 30. Observability

Basic metrics are desirable.

Potential metrics:

```text
http_requests_total
http_request_duration
meeting_booking_success_total
meeting_booking_conflict_total
availability_query_total
```

The purpose is not to build a complete production monitoring platform, but to demonstrate awareness of operating backend services.

Metrics are an explicit "plus" in the challenge.

---

# 31. Definition of Done

Version 1 is considered complete when:

* [x] The service is implemented with Java and Spring Boot.
* [x] PostgreSQL is used for persistence.
* [x] Users can be created and retrieved.
* [x] Users can create time slots.
* [x] Users can list time slots.
* [x] Users can update free time slots.
* [x] Users can delete free time slots.
* [x] Users can book meetings.
* [x] Meetings contain title and description.
* [x] Meetings contain participants.
* [x] Booked slots become `BUSY`.
* [x] Busy slots cannot be booked again.
* [x] Concurrent booking attempts cannot double-book a slot.
* [x] Availability can be queried for a timeframe.
* [x] Availability can be filtered by free/busy status.
* [x] Adjacent availability periods are aggregated.
* [x] Data survives application restarts.
* [x] Automated tests cover the core business rules.
* [x] Integration tests verify PostgreSQL behavior.
* [x] The application runs through Docker Compose.
* [x] README documents setup and API consumption.
* [x] Meaningful Git commits are used.
* [x] Basic metrics are exposed.

---

# 32. Explicit Design Decisions

The following decisions are intentional:

## Modular monolith

The expected scale does not justify microservices.

## PostgreSQL

The domain has strong consistency requirements and relational relationships between users, slots, meetings, and participants.

## UUID identifiers

**Decision:** Use UUID for all primary keys.

**Reason:** Good API identifiers; no information leakage through sequential IDs; easy to generate in the application; no dependency on database-generated sequential identifiers.

## Timestamp representation

**Decision:** PostgreSQL `TIMESTAMPTZ` + Java `Instant`.

**Reason:** We are representing points in time, so `Instant` is the right domain representation. Avoids ambiguity even though timezone conversion is out of scope.

## Database-enforced invariants

Correctness constraints should not rely exclusively on application-level checks.

### Slot overlap prevention

**Decision:** PostgreSQL exclusion constraint using `tstzrange`.

**Reason:** Prevents race conditions and makes the no-overlap invariant database-enforced. The `[)` half-open range semantics allow adjacent slots (e.g., `09:00-10:00` and `10:00-11:00`) while rejecting overlapping ones.

### Meeting uniqueness

**Decision:** `UNIQUE(slot_id)` on `meetings`.

**Reason:** Database-level defense against multiple meetings per slot, providing defense in depth alongside application-level transaction locking.

## Transactional booking

Creating a meeting and transitioning a slot to `BUSY` must be atomic.

### Booking concurrency

**Decision:** Transaction + `SELECT FOR UPDATE`.

**Reason:** Serializes concurrent booking attempts for the same slot and makes the state transition deterministic. The second transaction waits for the row lock, then reads the updated `BUSY` state and returns `409 Conflict`.

## No meeting cancellation

This simplifies the state machine and keeps the challenge focused.

## No authentication

Authentication is outside the problem domain and would add implementation complexity without demonstrating the core scheduling design.

## Calendar as domain concept

Calendar exists conceptually in the domain but does not become an unnecessary CRUD resource.

## Availability aggregation

**Decision:** Database performs timeframe filtering; application performs clipping and aggregation.

**Reason:** Aggregation is domain behavior, not merely persistence logic. At the expected scale, this is trivial computationally.

## Slot listing vs availability listing

**Decision:** `GET /slots` returns the actual persisted slot boundaries, while `GET /availability` returns clipped + aggregated periods.

**Reason:** This keeps the resource API truthful and makes availability the derived view.

---

# 33. Future Extensions

Possible future versions could introduce:

* Meeting cancellation.
* Meeting rescheduling.
* Recurring availability.
* Time-zone aware scheduling.
* Authentication and authorization.
* External calendar integrations.
* Notifications.
* Search across multiple participants' availability.
* Caching for heavily queried calendars.
* Rate limiting.
* Distributed deployment.
* Advanced observability.

These are deliberately excluded from version 1.

---

# 34. Implementation Principle

The implementation should follow this specification.

When a technical decision is required that is not explicitly defined here, the decision should:

1. Preserve the domain invariants.
2. Prefer correctness over premature optimization.
3. Prefer simple designs appropriate for the expected scale.
4. Be documented when it materially affects the architecture.
5. Be covered by tests when it affects observable behavior.

The specification may be amended during implementation when a discovered technical constraint requires it. Any such change should be documented as a deliberate design decision rather than silently changing the behavior.
