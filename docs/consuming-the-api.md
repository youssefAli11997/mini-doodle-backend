# Consuming the Mini Doodle API

This guide explains how to run the service locally and exercise the HTTP API end to end. No authentication is required in version 1 — every request is anonymous.

**Base URL (default):** `http://localhost:8080`

---

## Prerequisites

- [Docker](https://docs.docker.com/get-docker/) (Docker Desktop on macOS/Windows is fine)
- Optional: `curl` for command-line testing
- Optional: VS Code / Cursor with the [REST Client](https://marketplace.visualstudio.com/items?itemName=humao.rest-client) extension, or IntelliJ HTTP Client, to run `api-examples.http`

You do **not** need Java or PostgreSQL installed on your host machine when using Docker Compose.

---

## 1. Start the service

From the repository root:

```bash
docker compose up --build
```

Wait until you see the Spring Boot application started. Then verify health:

```bash
curl http://localhost:8080/actuator/health
```

Expected:

```json
{"status":"UP", ...}
```

### Port conflicts

If you see `bind: address already in use` for port `5432` or `8080`, override the host ports:

```bash
API_PORT=18080 POSTGRES_PORT=55432 docker compose up --build
```

Then use `http://localhost:18080` as the base URL for all examples below.

You can persist overrides by creating a `.env` file in the repo root:

```env
API_PORT=18080
POSTGRES_PORT=55432
```

Stop the stack:

```bash
docker compose down
```

Data is stored in a Docker volume and survives container restarts until you remove the volume with `docker compose down -v`.

---

## 2. Quick start — happy path

The typical flow is: **create users → create a slot → book a meeting → query availability**.

Set the base URL (adjust if you used a custom port):

```bash
export BASE=http://localhost:8080
```

### Step 1 — Create two users

The slot owner and at least one other participant are required to book a meeting.

```bash
curl -s -X POST "$BASE/users" \
  -H 'Content-Type: application/json' \
  -d '{"name":"Alice","email":"alice@example.com"}'
```

Example response (`201 Created`):

```json
{
  "id": "e05eb555-d1e7-4769-99b6-e7e3f256a99d",
  "name": "Alice",
  "email": "alice@example.com",
  "createdAt": "2026-08-30T13:46:21.199350503Z",
  "updatedAt": "2026-08-30T13:46:21.199350503Z"
}
```

```bash
curl -s -X POST "$BASE/users" \
  -H 'Content-Type: application/json' \
  -d '{"name":"Bob","email":"bob@example.com"}'
```

Save the IDs:

```bash
export OWNER_ID=<alice-id-from-response>
export PARTICIPANT_ID=<bob-id-from-response>
```

### Step 2 — Create a free time slot

```bash
curl -s -X POST "$BASE/users/$OWNER_ID/slots" \
  -H 'Content-Type: application/json' \
  -d '{"startTime":"2026-09-01T09:00:00Z","endTime":"2026-09-01T10:00:00Z"}'
```

Example response (`201 Created`):

```json
{
  "id": "7c4c4e4d-c52b-44a1-848f-2fb29a713805",
  "userId": "e05eb555-d1e7-4769-99b6-e7e3f256a99d",
  "startTime": "2026-09-01T09:00:00Z",
  "endTime": "2026-09-01T10:00:00Z",
  "status": "FREE",
  "createdAt": "2026-08-30T13:46:21.318899919Z",
  "updatedAt": "2026-08-30T13:46:21.318899919Z"
}
```

```bash
export SLOT_ID=<slot-id-from-response>
```

### Step 3 — Book a meeting

Rules:

- The slot owner **must** appear in `participantIds`
- At least **two** distinct participants are required
- Participant IDs must exist and be unique
- The slot must be `FREE`

```bash
curl -s -X POST "$BASE/users/$OWNER_ID/slots/$SLOT_ID/meeting" \
  -H 'Content-Type: application/json' \
  -d "{\"title\":\"Architecture Discussion\",\"description\":\"Discuss backend architecture\",\"participantIds\":[\"$OWNER_ID\",\"$PARTICIPANT_ID\"]}"
```

Example response (`201 Created`):

```json
{
  "id": "1622a1ac-59b6-477c-8adc-e8b8e6ec25f3",
  "slotId": "7c4c4e4d-c52b-44a1-848f-2fb29a713805",
  "title": "Architecture Discussion",
  "description": "Discuss backend architecture",
  "participantIds": ["e05eb555-d1e7-4769-99b6-e7e3f256a99d", "548993f0-121a-4862-9afd-d19d0015aa54"],
  "slot": {
    "id": "7c4c4e4d-c52b-44a1-848f-2fb29a713805",
    "status": "BUSY",
    "startTime": "2026-09-01T09:00:00Z",
    "endTime": "2026-09-01T10:00:00Z"
  }
}
```

```bash
export MEETING_ID=<meeting-id-from-response>
```

### Step 4 — Query availability

Returns clipped, aggregated free/busy periods for the requested window:

```bash
curl -s "$BASE/users/$OWNER_ID/availability?from=2026-09-01T08:00:00Z&to=2026-09-01T18:00:00Z"
```

Example response (`200 OK`):

```json
[
  {
    "startTime": "2026-09-01T09:00:00Z",
    "endTime": "2026-09-01T10:00:00Z",
    "status": "BUSY"
  }
]
```

Filter to free or busy only with `&status=FREE` or `&status=BUSY`.

### Step 5 — Retrieve the meeting

```bash
curl -s "$BASE/users/$OWNER_ID/meetings/$MEETING_ID"
```

The slot owner or any listed participant can retrieve the meeting.

---

## 3. Using `api-examples.http`

The repository root contains [`api-examples.http`](../api-examples.http) with one request block per endpoint.

1. Open the file in Cursor, VS Code (REST Client extension), or IntelliJ.
2. Set `@baseUrl` if you are not on the default port.
3. Run **Create owner** and **Create participant**.
4. Copy the returned IDs into `@ownerId` and `@participantId` at the top of the file.
5. Run **Create slot**, then set `@slotId`.
6. Run **Book meeting**, then set `@meetingId`.
7. Execute the remaining requests in any order.

This is the fastest way to explore the API interactively without writing shell scripts.

---

## 4. Endpoint reference

All timestamps are ISO-8601 UTC instants, e.g. `2026-09-01T09:00:00Z`.

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/users` | Create a user |
| `GET` | `/users/{userId}` | Get a user |
| `POST` | `/users/{userId}/slots` | Create a `FREE` slot |
| `GET` | `/users/{userId}/slots` | List slots (optional `from`, `to`, `status`) |
| `GET` | `/users/{userId}/slots/{slotId}` | Get one slot |
| `PATCH` | `/users/{userId}/slots/{slotId}` | Update a `FREE` slot |
| `DELETE` | `/users/{userId}/slots/{slotId}` | Delete a `FREE` slot |
| `POST` | `/users/{userId}/slots/{slotId}/meeting` | Book a meeting |
| `GET` | `/users/{userId}/meetings/{meetingId}` | Get a meeting |
| `GET` | `/users/{userId}/availability` | Query availability (`from` and `to` required) |
| `GET` | `/actuator/health` | Health check |
| `GET` | `/actuator/prometheus` | Prometheus metrics |

### List slots vs availability

- **`GET /slots`** returns persisted slot boundaries. Partial overlaps with the query range are **clipped** in the response.
- **`GET /availability`** returns a derived calendar view: clipped, sorted, and **aggregated** so adjacent periods with the same status are merged.

---

## 5. Error responses

Every error returns the same JSON shape:

```json
{
  "code": "SLOT_ALREADY_BOOKED",
  "message": "The requested time slot is already booked."
}
```

Common status codes:

| HTTP status | When |
|-------------|------|
| `400 Bad Request` | Invalid body, missing required query param, invalid enum value |
| `404 Not Found` | User, slot, or meeting does not exist (or slot/meeting not on that user's calendar) |
| `409 Conflict` | Duplicate email, overlapping slot, slot already booked, modifying/deleting a `BUSY` slot |
| `500 Internal Server Error` | Unexpected server failure |

### Scenarios worth trying

| Action | Expected |
|--------|----------|
| Book the same slot twice | `409` / `SLOT_ALREADY_BOOKED` |
| Create overlapping slots for one user | `409` / `SLOT_OVERLAP` |
| Update or delete a `BUSY` slot | `409` / `SLOT_NOT_FREE` |
| Book with only the owner as participant | `400` / `INVALID_PARTICIPANTS` |
| Book without the owner in `participantIds` | `400` / `INVALID_PARTICIPANTS` |
| Availability with `status=UNKNOWN` | `400` / `BAD_REQUEST` |
| Duplicate email on user creation | `409` / `EMAIL_ALREADY_EXISTS` |

---

## 6. Observability

Prometheus metrics:

```bash
curl -s "$BASE/actuator/prometheus" | grep -E 'meeting_booking|availability_query'
```

Application counters:

- `meeting_booking_success_total`
- `meeting_booking_conflict_total`
- `availability_query_total`

After a successful booking and a failed re-book attempt, you should see both success and conflict counters increment.

---

## 7. Run automated tests

To verify the full behaviour suite (including concurrency and database constraints):

```bash
./mvnw clean verify
```

Docker must be running — tests spin up PostgreSQL via Testcontainers.

---

## 8. Further reading

- [Specification](spec.md) — full functional requirements and domain rules
- [README](../README.md) — architecture, technology choices, and design decisions
