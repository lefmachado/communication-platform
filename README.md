# 📨 Communication API — Sprint 1

![Java](https://img.shields.io/badge/Java-25-orange?style=flat-square&logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4+-brightgreen?style=flat-square&logo=springboot)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-17-blue?style=flat-square&logo=postgresql)
![Flyway](https://img.shields.io/badge/Flyway-Migrations-CC0200?style=flat-square&logo=flyway)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=flat-square&logo=docker)
![Status](https://img.shields.io/badge/Status-Work%20in%20Progress-yellow?style=flat-square)

> 🚧 **Development Status:** This project is currently under active development as the Sprint 1 delivery of a multichannel communication platform (`EMAIL`, `SMS`, `PUSH`, and `WHATSAPP`). The sections below contain the full original challenge specification followed by architectural decisions, database modeling, API contracts, and implementation progress.

---

## 📚 Project Documentation

- **[Changelog](CHANGELOG.md):** Chronological log of notable changes per release and issue.
- **[Contributing Guidelines](CONTRIBUTING.md):** Branching strategy, commit conventions, and PR workflow.
- **[License](LICENSE):** Open-source license governing this repository.

---

## 📄 Original Challenge Specification

### Backend Technical Challenge — Communication Platform (Sprint 1)

#### 🎯 Scenario & Objective
The company has the challenge of developing a communication platform, and you have been chosen to start the development of the first sprint.

The objective of this sprint is to provide **3 services (endpoints)** related to sending company communications:
1. **Schedule** a communication delivery;
2. **Query** a scheduled communication status;
3. **Cancel** a scheduled communication.

#### ⚙️ Requirements
- Create an endpoint that receives a request to schedule a communication delivery **(1)** containing, at minimum, the following fields:
  - Scheduled date/time for delivery
  - Recipient
  - Message to be delivered
- Supported communication channels:
  - `email`, `sms`, `push`, and `whatsapp`
- **Actual message dispatching is out of scope for this sprint**—you do not need to implement the delivery mechanism itself.
- For this sprint, scheduling requests must be persisted in the database immediately upon receipt.
- **Database design foresight:** Even though another team/colleague will implement the actual dispatching later, the database schema must already be structured so that no schema changes are needed when delivery is implemented.
- During delivery, the worker's responsibility will be to dispatch the message and update the record's status in the database. Account for this in the data model.
- Provide an endpoint to **query the status** of a scheduled communication **(2)**.
- Provide an endpoint to **cancel** a scheduled communication **(3)**.

#### 💡 General Guidelines & Evaluation Criteria
- Preferred languages: **Java**, Python, or Node (any other language is accepted with justification for the choice).
- Database options: **PostgreSQL** or MySQL.
- APIs must follow the **RESTful** model with standard **JSON** payloads.
- Write **unit tests**, focusing on a well-organized and automated test suite.
- Follow clean code, software engineering, and version control best practices (small, descriptive commits).
- Database creation (scripts, migrations, or application-managed) is up to the developer.
- Submit via a public Git repository with an **open-source license**, containing only source code and documentation (no binaries, images, videos, or audio files).
- Include clear instructions on how to install, test, and run the project.
- **Bonus:** Provide Docker / Docker Compose setup to run the project locally.

---

## 🏗️ How It Was Implemented

### 1. Tech Stack & Rationale
- **Java 25 & Spring Boot 3.4+:** Mature ecosystem for high-availability microservices, offering strong typing, immutable Java `record` DTOs, declarative Bean Validation, clean dependency injection, and seamless extensibility toward messaging and observability in future sprints.
- **PostgreSQL 17:** Robust relational database with native `UUID` support, `TIMESTAMP WITH TIME ZONE` (preventing timezone ambiguity in scheduled dispatches), and high-performance composite indexes for future worker polling.
- **Flyway:** Explicit, versioned, and auditable SQL schema migrations (`src/main/resources/db/migration`), avoiding implicit schema drift across environments.
- **Docker & Docker Compose:** Standardized local execution bringing up both the API and PostgreSQL with a single command.
- **JUnit 5, Mockito & Testcontainers:** Automated test suite covering domain rules (unit tests) and real HTTP contracts + PostgreSQL persistence (integration tests).

### 2. Relational Database Modeling *(Dispatch-Ready)*
The `communication_schedules` table was designed so the upcoming delivery worker team can poll pending jobs, dispatch messages, and transition states **without any structural migrations**:

```sql
CREATE TABLE communication_schedules (
    id UUID PRIMARY KEY,
    scheduled_at TIMESTAMP WITH TIME ZONE NOT NULL,
    recipient VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    channel VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    sent_at TIMESTAMP WITH TIME ZONE,
    error_message VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_comm_status_scheduled_at
    ON communication_schedules (status, scheduled_at);
```

#### Status Lifecycle (`CommunicationStatus`)
- **`SCHEDULED`:** Initial state assigned when a communication request is persisted.
- **`SENT`:** Reserved for the future delivery worker to mark successful delivery (populates `sent_at`).
- **`FAILED`:** Reserved for the worker to record delivery failures (populates `error_message` for traceability/retries).
- **`CANCELED`:** Soft-deletion state assigned via the cancellation endpoint, preventing the worker from dispatching canceled messages.

### 3. Layer Structure *(In Progress)*
```text
src/main/java/br/com/lefmachado/communication
├── controller/   # REST endpoints (POST, GET, DELETE)
├── service/      # Business rules, state transitions & validations
├── repository/   # Spring Data JPA interfaces
├── entity/       # JPA @Entity and domain enums (CommunicationChannel, CommunicationStatus)
├── dto/          # Immutable Java record contracts for Request/Response
└── exception/    # Global exception handling (@RestControllerAdvice + RFC 7807 ProblemDetail)
```

### 4. REST API Contracts (JSON)

| Method | Endpoint | Description | HTTP Status |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/communications` | Schedule a new communication delivery | `201 Created` / `400 Bad Request` |
| `GET` | `/api/v1/communications/{id}` | Query details and current status of a schedule | `200 OK` / `404 Not Found` |
| `DELETE` | `/api/v1/communications/{id}` | Cancel a scheduled communication (`status = CANCELED`) | `204 No Content` / `404 Not Found` / `409 Conflict` |

#### Example Request — Schedule Communication (`POST /api/v1/communications`)
```json
{
  "scheduledAt": "2026-12-01T14:30:00-03:00",
  "recipient": "customer@example.com",
  "message": "Your order #12345 is out for delivery!",
  "channel": "EMAIL"
}
```

#### Example Response (`201 Created` / `200 OK`)
```json
{
  "id": "8f9b2c10-4e3a-4b1d-9c2a-7f8e6d5c4b3a",
  "scheduledAt": "2026-12-01T14:30:00-03:00",
  "recipient": "customer@example.com",
  "message": "Your order #12345 is out for delivery!",
  "channel": "EMAIL",
  "status": "SCHEDULED",
  "createdAt": "2026-11-19T18:00:00-03:00"
}
```

---

## 🚀 Implementation Roadmap

### Sprint 1: Core Scheduling API *(Challenge Scope)*
- [ ] Project scaffolding (Java 25, Spring Boot, JPA, PostgreSQL, Flyway, Lombok) and open-source license
- [ ] PostgreSQL container setup (`compose.yaml`) and Flyway initial migration (`V1__create_communication_schedules_table.sql`)
- [ ] Domain model (`CommunicationSchedule`, `CommunicationChannel`, `CommunicationStatus`) & Repository
- [ ] Schedule, Query, and Cancel endpoints (`/api/v1/communications`) with Bean Validation
- [ ] Global exception handler (`@RestControllerAdvice` with RFC 7807 `ProblemDetail`)
- [ ] OpenAPI 3 / Swagger UI documentation (`springdoc-openapi`)
- [ ] Unit tests (JUnit 5 + Mockito) and integration tests (Testcontainers + PostgreSQL)

### Future Sprints *(Platform Evolution)*
- [ ] Asynchronous dispatcher worker using PostgreSQL `SKIP LOCKED` polling or message broker (RabbitMQ/Kafka)
- [ ] Channel provider adapters (`EMAIL`, `SMS`, `PUSH`, `WHATSAPP`) with retry policies and Dead Letter Queues (DLQ)
- [ ] Idempotency key support (`Idempotency-Key` header) on `POST /api/v1/communications`

---

## 💻 Running the Project Locally

### Prerequisites
- **Docker** and **Docker Compose**
- *(Optional, to run outside containers)* **Java 25+** and **Maven 3.9+**

### Option 1: Docker Compose *(Recommended)*
```bash
git clone [https://github.com/lefmachado/communication-api.git](https://github.com/lefmachado/communication-api.git)
cd communication-api
docker compose up -d --build
```
The API will be available at `http://localhost:8080` and the interactive OpenAPI/Swagger UI at `http://localhost:8080/swagger-ui.html`.

### Option 2: Running Locally via Maven
```bash
# Start PostgreSQL only
docker compose up -d postgres

# Run the application
./mvnw spring-boot:run
```

---

## 🧪 Running Automated Tests

```bash
# Run unit and integration tests
./mvnw test
```

---

## 📄 License

This project is licensed under the **MIT License** — see the [LICENSE](LICENSE) file for details.