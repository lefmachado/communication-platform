# 📋 Requirements & Problem Analysis — Communication Platform API

This document formalizes the problem space, user needs, functional/non-functional requirements, constraints, and acceptance criteria guiding the evolution of `communication-platform` (`communication-api`) from Sprint 1 (Core Scheduling REST API) to future sprints (asynchronous multichannel dispatching, provider adapters, retries/DLQ, and idempotency).

---

## 1. Problem Space, Workflow & Digital Solution

| Dimension | Context in the Communication Platform Ecosystem |
| :--- | :--- |
| **Public / Business Problem** | Fragmented notification triggers across company services, lack of centralized scheduling and delivery status visibility, inability to cancel queued messages before dispatch, and tight coupling between business applications and external messaging providers (`EMAIL`, `SMS`, `PUSH`, `WHATSAPP`). |
| **Workflow (*As-Is* $\rightarrow$ *To-Be*)** | **As-Is:** Services trigger notifications ad-hoc or synchronously with no unified schedule persistence, status tracking, audit trail, or cancellation capability.<br>**To-Be:** Clients schedule multichannel communications through a single RESTful API persisted immediately in a dispatch-ready PostgreSQL store, query real-time delivery status, or soft-cancel pending schedules, while future asynchronous workers poll and dispatch messages without requiring schema changes. |
| **Digital Solution** | Standardized JSON REST API (`/api/v1/communications`), dispatch-ready relational persistence with PostgreSQL 17 and Flyway migrations, an explicit status lifecycle (`SCHEDULED`, `SENT`, `FAILED`, `CANCELED`), and an extensible architecture ready for asynchronous workers and channel provider adapters. |

---

## 2. User Needs & Personas

- **Internal Client Service / Backend Integrator:** Needs a simple, reliable REST API to schedule multichannel communications (`EMAIL`, `SMS`, `PUSH`, `WHATSAPP`) with timezone-aware timestamps, query the current status of a schedule by UUID, and cancel pending communications before they are sent.
- **Delivery Worker Team (Future Sprints):** Needs a stable, dispatch-ready database schema containing lifecycle status (`SCHEDULED`, `SENT`, `FAILED`, `CANCELED`), delivery metadata (`sent_at`, `error_message`), and a composite polling index `(status, scheduled_at)` so message dispatching can be built without structural database migrations.
- **Customer / End Recipient:** Expects timely notifications on the designated communication channel and expects canceled communications never to be delivered.
- **Operations & Support Team:** Needs auditable scheduling history (soft cancellation rather than physical deletion, `created_at`/`updated_at`, failure `error_message`) and clear, standardized error responses (RFC 7807 `ProblemDetail`) for troubleshooting.

---

## 3. Functional Requirements (FR)

- **FR01 — Schedule Communication (`POST /api/v1/communications`):** Receive and immediately persist a communication scheduling request containing, at minimum, `scheduledAt` (date/time with timezone), `recipient`, `message`, and `channel` (`EMAIL`, `SMS`, `PUSH`, `WHATSAPP`), initializing `status = SCHEDULED` and returning `201 Created`.
- **FR02 — Query Communication Status (`GET /api/v1/communications/{id}`):** Retrieve the full details and current lifecycle status (`SCHEDULED`, `SENT`, `FAILED`, `CANCELED`) of a scheduled communication by its `UUID`, returning `200 OK` or `404 Not Found`.
- **FR03 — Cancel Scheduled Communication (`DELETE /api/v1/communications/{id}`):** Soft-cancel a communication currently in `SCHEDULED` status by transitioning it to `CANCELED` (`204 No Content`), returning `404 Not Found` if the ID does not exist and `409 Conflict` if the record is no longer cancelable (e.g., already `SENT` or `CANCELED`).
- **FR04 — Multichannel Support:** Support `EMAIL`, `SMS`, `PUSH`, and `WHATSAPP` channels via a strongly typed `CommunicationChannel` enum with declarative Bean Validation.
- **FR05 — Dispatch-Ready Data Model (Sprint 1 Boundary):** Actual message dispatching is out of scope for Sprint 1; however, the `communication_schedules` table must already store `status`, `sent_at`, `error_message`, `created_at`, and `updated_at` so future delivery workers can dispatch messages and update status with zero schema changes.

---

## 4. Non-Functional Requirements (NFR)

- **NFR01 — Temporal Accuracy & Timezone Safety:** All temporal columns (`scheduled_at`, `sent_at`, `created_at`, `updated_at`) must use `TIMESTAMP WITH TIME ZONE` in PostgreSQL to eliminate timezone ambiguity in scheduled dispatches.
- **NFR02 — Schema Versioning & Reproducibility:** Manage all database schema changes through explicit, versioned Flyway SQL migrations (`src/main/resources/db/migration`) and provide a Docker Compose setup for consistent local execution.
- **NFR03 — API Ergonomics & Standards:** Expose stateless RESTful JSON endpoints with immutable Java `record` DTOs, declarative Bean Validation, OpenAPI 3 / Swagger UI documentation, and RFC 7807 (`ProblemDetail`) error payloads via `@RestControllerAdvice`.
- **NFR04 — Automated Testability:** Maintain a well-organized automated test suite combining unit tests (JUnit 5 + Mockito) for domain/service rules and integration tests (Testcontainers + PostgreSQL) for HTTP contracts and real database persistence.
- **NFR05 — Future Scalability & Resilience (Future Sprints):** Design indexing (`idx_comm_status_scheduled_at` on `(status, scheduled_at)`) to support high-throughput asynchronous worker polling (`SKIP LOCKED`) or message broker integration (RabbitMQ/Kafka), retry policies with Dead Letter Queues (DLQ), and `Idempotency-Key` support on `POST /api/v1/communications`.

---

## 5. Constraints

- **Technical Constraints:** Java 25, Spring Boot 4.1+, Spring Data JPA / Hibernate, PostgreSQL 17, Flyway, Lombok, Docker Compose, and stateless JSON REST API under `/api/v1/communications`.
- **Domain & Scope Constraints:** Actual message delivery to external providers is out of scope for Sprint 1; `scheduledAt`, `recipient`, `message`, and `channel` are mandatory; `scheduledAt` must represent a future timestamp at scheduling time; cancellation is a soft state transition (`CANCELED`) permitted only when the current status is `SCHEDULED`.

---

## 6. Core Acceptance Criteria (BDD / Gherkin)

> *Detailed, feature-specific acceptance criteria are tracked directly inside each GitHub Issue and validated via automated tests.*

### Feature: Schedule Communication

```gherkin
Scenario 1: Schedule a valid communication delivery
  Given a client has a valid payload with a future scheduledAt, recipient, message, and channel "EMAIL"
  When the client sends POST /api/v1/communications
  Then the system persists the record immediately in PostgreSQL with status "SCHEDULED"
  And returns HTTP 201 Created with the generated UUID and scheduled communication details

Scenario 2: Reject an invalid scheduling request
  Given a client sends POST /api/v1/communications with a missing recipient, past scheduledAt, or unsupported channel
  When the request is validated
  Then the API rejects the operation with HTTP 400 Bad Request
  And returns an RFC 7807 ProblemDetail payload describing the validation errors
```

### Feature: Query Communication Status

```gherkin
Scenario 3: Query an existing scheduled communication
  Given a communication schedule exists with UUID "8f9b2c10-4e3a-4b1d-9c2a-7f8e6d5c4b3a" and status "SCHEDULED"
  When a client sends GET /api/v1/communications/8f9b2c10-4e3a-4b1d-9c2a-7f8e6d5c4b3a
  Then the API returns HTTP 200 OK with the schedule details and status "SCHEDULED"

Scenario 4: Query a non-existent communication
  Given no communication schedule exists for a given UUID
  When a client sends GET /api/v1/communications/{id}
  Then the API returns HTTP 404 Not Found with an RFC 7807 ProblemDetail payload
```

### Feature: Cancel Scheduled Communication

```gherkin
Scenario 5: Soft-cancel a scheduled communication
  Given a communication schedule exists with status "SCHEDULED"
  When a client sends DELETE /api/v1/communications/{id}
  Then the system updates its status to "CANCELED" without physically deleting the row
  And returns HTTP 204 No Content

Scenario 6: Prevent canceling a communication that was already sent or canceled
  Given a communication schedule exists with status "SENT" or "CANCELED"
  When a client sends DELETE /api/v1/communications/{id}
  Then the API rejects the operation with HTTP 409 Conflict
```
