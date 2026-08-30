# Mini Doodle Backend

Mini Doodle is a Spring Boot backend for persistent meeting scheduling. Users create free time slots, book meetings against those slots, and query free/busy availability over a requested time range.

**→ [How to consume the API](docs/consuming-the-api.md)** — step-by-step setup, curl walkthrough, HTTP file examples, and endpoint reference.

## Architecture

The application is a modular monolith with package boundaries by feature:

- `user`: user creation and lookup
- `slot`: time-slot CRUD and overlap protection
- `meeting`: transactional booking and meeting retrieval
- `availability`: clipped and aggregated free/busy views
- `common.exception`: consistent API error mapping
- `observability`: Micrometer counters for service behavior

Controllers call application services, services enforce business rules and transactions, and Spring Data JPA repositories persist to PostgreSQL. Flyway owns the database schema.

## Technology Choices

- Java 21
- Spring Boot 3.3
- Spring Web, Validation, Data JPA, Actuator
- PostgreSQL 16
- Flyway migrations
- Testcontainers for integration tests against PostgreSQL
- Micrometer with Prometheus exposition
- Docker Compose for local execution

## Run Locally

Start PostgreSQL and the API:

```bash
docker compose up --build
```

The API listens on `http://localhost:8080`. If host ports are already in use (common when local PostgreSQL uses `5432`), override them:

```bash
API_PORT=18080 POSTGRES_PORT=55432 docker compose up --build
```

Or add a `.env` file with those variables and run `docker compose up --build`.

Useful health and metrics endpoints:

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8080/actuator/prometheus
```

See **[Consuming the API](docs/consuming-the-api.md)** for a full walkthrough, port-conflict troubleshooting, and example requests.

## Run Tests

```bash
./mvnw clean verify
```

The test suite uses Testcontainers, so Docker must be available.

## API

Endpoint summary and request shapes. For a guided happy path, error scenarios, and copy-paste `curl` examples, use **[docs/consuming-the-api.md](docs/consuming-the-api.md)**. Ready-made requests are also in [`api-examples.http`](api-examples.http) (VS Code REST Client / IntelliJ HTTP Client).

All timestamps are ISO-8601 instants, for example `2026-09-01T09:00:00Z`.

### Create User

```http
POST /users
Content-Type: application/json

{
  "name": "Alice",
  "email": "alice@example.com"
}
```

Returns `201 Created`.

### Get User

```http
GET /users/{userId}
```

Returns `200 OK` or `404 Not Found`.

### Create Slot

```http
POST /users/{userId}/slots
Content-Type: application/json

{
  "startTime": "2026-09-01T09:00:00Z",
  "endTime": "2026-09-01T10:00:00Z"
}
```

Returns a `FREE` slot with `201 Created`. Invalid ranges return `400 Bad Request`; overlapping slots return `409 Conflict`.

### List Slots

```http
GET /users/{userId}/slots?from=2026-09-01T08:00:00Z&to=2026-09-01T18:00:00Z&status=FREE
```

`from`, `to`, and `status` are optional for slot listing. When a slot partially overlaps the requested range, the response clips `startTime` and `endTime` to the requested bounds.

### Get Slot

```http
GET /users/{userId}/slots/{slotId}
```

Returns `200 OK` or `404 Not Found`.

### Update Slot

```http
PATCH /users/{userId}/slots/{slotId}
Content-Type: application/json

{
  "startTime": "2026-09-01T09:30:00Z",
  "endTime": "2026-09-01T10:30:00Z"
}
```

Only `FREE` slots can be updated. Busy slots and overlapping updates return `409 Conflict`.

### Delete Slot

```http
DELETE /users/{userId}/slots/{slotId}
```

Only `FREE` slots can be deleted. Successful deletion returns `204 No Content`.

### Book Meeting

```http
POST /users/{userId}/slots/{slotId}/meeting
Content-Type: application/json

{
  "title": "Architecture Discussion",
  "description": "Discuss backend architecture",
  "participantIds": [
    "{userId}",
    "{otherUserId}"
  ]
}
```

The slot owner must be included in `participantIds`, participant IDs must be unique, every participant must exist, and at least two participants are required. A successful booking returns `201 Created`, creates a meeting, links participants, and changes the slot to `BUSY`.

### Get Meeting

```http
GET /users/{userId}/meetings/{meetingId}
```

Returns meeting details, the full booked slot, and participant IDs. The current implementation allows the slot owner or a listed participant to retrieve the meeting.

### Query Availability

```http
GET /users/{userId}/availability?from=2026-09-01T08:00:00Z&to=2026-09-01T18:00:00Z&status=BUSY
```

`from` and `to` are required. `status` is optional and accepts `FREE` or `BUSY`. Results are clipped to the requested range, sorted by start time, and adjacent periods with the same status are merged.

## Error Responses

Errors use a consistent JSON structure:

```json
{
  "code": "SLOT_ALREADY_BOOKED",
  "message": "The requested time slot is already booked."
}
```

Status mapping:

- `400 Bad Request`: invalid input, malformed JSON, invalid query parameter, missing required query parameter
- `404 Not Found`: missing user, slot, or meeting
- `409 Conflict`: duplicate email, overlapping slot, busy slot, already booked slot
- `500 Internal Server Error`: unexpected server error

## Observability

Prometheus metrics are exposed at:

```http
GET /actuator/prometheus
```

Application counters:

- `meeting_booking_success_total`
- `meeting_booking_conflict_total`
- `availability_query_total`

Spring Boot Actuator also exposes HTTP server request metrics such as `http_server_requests_seconds_count`.

## Design Decisions

- PostgreSQL exclusion constraint: `time_slots` uses a GiST exclusion constraint over `user_id` and `tstzrange(start_time, end_time, '[)')` so a user cannot have overlapping slots, even under concurrent requests.
- Half-open intervals: `[)` semantics allow adjacent slots like `09:00-10:00` and `10:00-11:00`.
- Transactional booking: booking uses a transaction plus pessimistic row locking on the target slot, equivalent to `SELECT ... FOR UPDATE`.
- Defense in depth: `meetings.slot_id` is unique, so the database cannot contain multiple meetings for one slot even if application logic regresses.
- UUID identifiers: IDs are generated in the application and exposed as opaque API identifiers.
- `Instant` timestamps: PostgreSQL `TIMESTAMPTZ` maps to Java `Instant` to represent precise points in time.

## Known Limitations

- No authentication or authorization.
- No meeting cancellation or rescheduling.
- Participants do not automatically get mirrored busy slots on their own calendars.
- No calendar synchronization, notifications, recurring events, or reminders.
- No frontend UI.

## Future Improvements

- Add authenticated user context and authorization checks.
- Add cancellation and rescheduling workflows.
- Add participant calendar conflict checks or mirrored busy holds.
- Add OpenAPI documentation.
- Add richer metric tags and dashboards.
- Add pagination for very large slot or meeting listings.
