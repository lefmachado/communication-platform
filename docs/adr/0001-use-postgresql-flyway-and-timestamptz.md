# 1. Use PostgreSQL 17, Flyway Migrations, UUIDs, and TIMESTAMPTZ

- **Status:** Accepted
- **Date:** 2026-11-20

## Context
A multichannel communication scheduling platform must store scheduled dispatch timestamps unambiguously across timezones, expose safe non-sequential identifiers on public REST endpoints, support efficient future worker polling, and evolve its schema deterministically across environments.

## Decision
Use PostgreSQL 17 as the primary relational database with:
- Native `UUID` primary keys on `communication_schedules`.
- `TIMESTAMP WITH TIME ZONE` (`TIMESTAMPTZ`) for `scheduled_at`, `sent_at`, `created_at`, and `updated_at`.
- A composite index `idx_comm_status_scheduled_at` on `(status, scheduled_at)` to optimize future worker polling.
- Versioned SQL migrations managed by Flyway under `src/main/resources/db/migration`.

## Consequences
- Eliminates timezone ambiguity for scheduled deliveries regardless of server or client locale.
- Prevents implicit schema drift between local, test (Testcontainers), and production environments.
- Prepares the database for high-performance, index-backed polling (`SKIP LOCKED`) in future sprints without requiring index retrofits.
