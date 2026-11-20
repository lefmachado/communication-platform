# 2. Design a Dispatch-Ready Schema and Soft-Cancel State Machine

- **Status:** Accepted
- **Date:** 2026-11-20

## Context
Sprint 1 delivers only scheduling, status querying, and cancellation endpoints; actual message delivery will be implemented in a subsequent sprint by another team/worker. Physically deleting canceled records would erase audit history, and omitting delivery tracking fields now would force breaking schema migrations when dispatching is introduced.

## Decision
Model `communication_schedules` upfront with full lifecycle and delivery tracking columns (`status`, `sent_at`, `error_message`, `created_at`, `updated_at`) governed by the `CommunicationStatus` enum (`SCHEDULED`, `SENT`, `FAILED`, `CANCELED`). Implement `DELETE /api/v1/communications/{id}` as a soft-cancellation transitioning `SCHEDULED` to `CANCELED`, and reject cancellation attempts on non-`SCHEDULED` records with `409 Conflict`.

## Consequences
- Future delivery workers can poll `SCHEDULED` records and update `status`, `sent_at`, and `error_message` with zero structural database changes.
- Preserves a complete audit trail for canceled communications and ensures workers never dispatch messages that were already canceled.
- Requires explicit state-transition validation in the service layer to prevent invalid transitions (e.g., canceling a `SENT` communication).
