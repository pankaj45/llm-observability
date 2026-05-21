# LLM Observability Platform

Production-grade AI observability and inference logging platform for multi-provider LLM applications.

Current status: phase 2 inference gateway MVP implementation. The repository now contains the WebFlux streaming inference API, Gemini provider adapter, PostgreSQL Flyway schema, Redis cancellation coordination, Kafka lifecycle publisher, contract validation, and focused service/controller tests. Docker image validation is deferred until the later image-testing pass.

## Specification Baseline

- [Project roadmap](docs/roadmap.md)
- [Repository structure](docs/repository-structure.md)
- [Architecture overview](docs/architecture/overview.md)
- [System context diagram](docs/architecture/system-context.md)
- [Container diagram](docs/architecture/container-diagram.md)
- [High-level sequence flows](docs/architecture/sequence-flows.md)
- [API contracts](docs/architecture/api-contracts.md)
- [Event schemas](docs/architecture/event-schemas.md)
- [Engineering standards](docs/engineering-standards.md)
- [Local development strategy](docs/local-development-strategy.md)
- [Deployment strategy](docs/deployment-strategy.md)
- [Observability strategy](docs/observability-strategy.md)
- [Phase 01 specification](docs/specs/phase-01-platform-bootstrap.md)
- [Phase 02 specification](docs/specs/phase-02-inference-gateway-mvp.md)
- [ADR index](docs/adr/README.md)

## Target Stack

- Frontend: Next.js, Mantine, TypeScript
- Backend: Spring Boot 3, Java 21, Spring WebFlux
- Streaming: Server-Sent Events
- Event bus: Kafka
- OLTP database: PostgreSQL
- Analytics database: ClickHouse
- Cache and coordination: Redis
- Metrics and dashboards: Prometheus, Grafana
- Tracing and telemetry: OpenTelemetry
- Deployment: Docker Compose, Kubernetes, Helm

## Architecture Principles

- Spec-driven development before implementation.
- Clean architecture with domain-driven boundaries.
- Reactive streaming for inference and observability flows.
- Event-driven integration between write, analytics, and notification workloads.
- Observability-first service design: traces, structured logs, metrics, health checks, and SLOs from the first implementation phase.
- Maintainability and extensibility over short-term code volume reduction.

## Spec-Driven Workflow

Every feature starts with a specification that records:

- Goals
- Scope
- Non-goals
- Assumptions
- Ambiguities
- API contracts
- Event schemas
- Data model impact
- Observability impact
- Acceptance criteria
- Risks
- Test strategy

Every major technical decision must create or update an ADR in `docs/adr`.

## Repository Status

This repository currently contains phase 2 inference gateway code and documentation. Ingestion, analytics, full conversation continuity, authentication, and UI workflows remain future phases.

## Local Development

The local development strategy is documented in [Local Development Strategy](docs/local-development-strategy.md).

Useful commands:

```text
npm install
make contracts
make test-backend
make test-frontend
make test
make dev
make down
```

Phase 2 local notes:

- Set `GEMINI_API_KEY` before running live Gemini streams.
- `POST /v1/inference/stream` always creates the conversation id on the backend; request bodies containing `conversationId` are rejected.
- Run `mvn -pl services/inference-gateway test` for the inference gateway test suite.
