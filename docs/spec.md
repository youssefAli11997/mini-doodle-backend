# Mini Doodle Backend Specification

**Version:** 1.0
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

The response must only include slots relevant to the requested timeframe.

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

## Users

```text
users
-----
id
name
email
created_at
updated_at
```

Constraints:

* `id` primary key
* `email` unique
* required fields non-null

---

## Time Slots

```text
time_slots
----------
id
user_id
start_time
end_time
status
created_at
updated_at
```

Constraints:

* `id` primary key
* `user_id` foreign key
* `start_time < end_time`
* valid status
* appropriate index on `(user_id, start_time, end_time)`

The database should participate in enforcing important correctness guarantees wherever practical.

---

## Meetings

```text
meetings
--------
id
slot_id
title
description
created_at
updated_at
```

Constraints:

* `id` primary key
* `slot_id` foreign key
* `slot_id` unique

The unique constraint on `slot_id` guarantees that a slot cannot have multiple meetings.

---

## Meeting Participants

```text
meeting_participants
--------------------
meeting_id
user_id
```

Constraints:

* composite primary key `(meeting_id, user_id)`
* foreign key to `meetings`
* foreign key to `users`

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

---

## Integration Tests

Integration tests should verify:

* Database persistence.
* Database constraints.
* Repository queries.
* Transactions.
* Meeting creation.
* Availability queries.

PostgreSQL should be used for integration tests rather than replacing database behavior with an in-memory approximation.

---

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

* [ ] The service is implemented with Java and Spring Boot.
* [ ] PostgreSQL is used for persistence.
* [ ] Users can be created and retrieved.
* [ ] Users can create time slots.
* [ ] Users can list time slots.
* [ ] Users can update free time slots.
* [ ] Users can delete free time slots.
* [ ] Users can book meetings.
* [ ] Meetings contain title and description.
* [ ] Meetings contain participants.
* [ ] Booked slots become `BUSY`.
* [ ] Busy slots cannot be booked again.
* [ ] Concurrent booking attempts cannot double-book a slot.
* [ ] Availability can be queried for a timeframe.
* [ ] Availability can be filtered by free/busy status.
* [ ] Adjacent availability periods are aggregated.
* [ ] Data survives application restarts.
* [ ] Automated tests cover the core business rules.
* [ ] Integration tests verify PostgreSQL behavior.
* [ ] The application runs through Docker Compose.
* [ ] README documents setup and API consumption.
* [ ] Meaningful Git commits are used.
* [ ] Basic metrics are exposed.

---

# 32. Explicit Design Decisions

The following decisions are intentional:

### Modular monolith

The expected scale does not justify microservices.

### PostgreSQL

The domain has strong consistency requirements and relational relationships between users, slots, meetings, and participants.

### Database-enforced invariants

Correctness constraints should not rely exclusively on application-level checks.

### Transactional booking

Creating a meeting and transitioning a slot to `BUSY` must be atomic.

### No meeting cancellation

This simplifies the state machine and keeps the challenge focused.

### No authentication

Authentication is outside the problem domain and would add implementation complexity without demonstrating the core scheduling design.

### Calendar as domain concept

Calendar exists conceptually in the domain but does not become an unnecessary CRUD resource.

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
