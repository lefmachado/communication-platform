# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Initial repository setup and Sprint 1 project documentation in `README.md`, covering challenge specification, tech stack rationale, dispatch-ready database schema, REST API contracts, and implementation roadmap.
- Initial Spring Boot project scaffolding with Java 25, PostgreSQL 17 via Docker Compose, and Flyway migrations.
- Repository governance files (`CONTRIBUTING.md`, `.editorconfig`, `CODEOWNERS`), GitHub templates, and MIT `LICENSE`.
- Requirements analysis (`docs/requirements.md`) and Architecture Decision Records (`docs/adr/0001` to `0003`) covering PostgreSQL 17 / Flyway / `TIMESTAMPTZ`, dispatch-ready schema with soft cancellation, and GitFlow / Conventional Commits.

[Unreleased]: https://github.com/lefmachado/communication-platform/compare/main...develop