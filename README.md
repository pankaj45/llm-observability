# LLM Observability Platform

Production-grade AI observability and inference logging platform for multi-provider LLM applications.

Current status: phase 5 analytics and operator UI implementation. The repository now contains the WebFlux streaming inference API, Gemini provider adapter, PostgreSQL Flyway schema, Redis cancellation and active replay coordination, Kafka lifecycle publisher, ingestion-worker lifecycle consumer pipeline, ClickHouse lifecycle fact schema, conversation metadata/message/timeline APIs, analytics query APIs, an operator dashboard, contract validation, and focused service/controller/ingestion tests. Docker image validation and live container-backed integration testing are deferred until explicitly requested.

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
- [Phase 6 production validation](docs/deployment/phase-06-production-validation.md)
- [Observability strategy](docs/observability-strategy.md)
- [Phase 01 specification](docs/specs/phase-01-platform-bootstrap.md)
- [Phase 02 specification](docs/specs/phase-02-inference-gateway-mvp.md)
- [Phase 03 specification](docs/specs/phase-03-inference-logging-pipeline.md)
- [Phase 04 specification](docs/specs/phase-04-conversation-continuity.md)
- [Phase 05 specification](docs/specs/phase-05-analytics-and-operator-ui.md)
- [Phase 06 specification](docs/specs/phase-06-production-hardening.md)
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

This repository currently contains phase 5 inference gateway, conversation continuity, ingestion-worker, analytics query, and operator dashboard code and documentation. Conversation summaries and context-window optimization, authentication, and production hardening remain future phases.

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
- `POST /v1/conversations/{conversationId}/messages/stream` continues an existing conversation; clients send only the new turn.
- Run `mvn -pl services/inference-gateway test` for the inference gateway test suite.

Phase 3 local notes:

- `services/ingestion-worker` consumes `inference.lifecycle.v1` when `INGESTION_KAFKA_ENABLED=true`.
- ClickHouse lifecycle fact writes are enabled with `INGESTION_CLICKHOUSE_ENABLED=true` and require `llm_observability.inference_lifecycle_fact` from `infra/migrations/clickhouse`.
- Run `mvn -pl services/ingestion-worker test` for the ingestion-worker test suite.

Phase 5 local notes:

- `services/analytics-query` reads ClickHouse lifecycle facts from `llm_observability.inference_lifecycle_fact`.
- Analytics APIs require `tenantId`, `projectId`, `from`, and `to`; query windows are capped at 30 days.
- Analytics responses exclude raw prompt and completion content.
- Run `mvn -pl services/analytics-query test` for the analytics query test suite.

Phase 6 local notes:

- API authentication is controlled by `SECURITY_ENABLED`; local defaults keep it disabled.
- Production Helm values enable OIDC/JWT validation and require bearer tokens for business APIs.
- Live container-backed PostgreSQL/Kafka/Redis/ClickHouse validation is documented in `docs/deployment/phase-06-production-validation.md` and can be run manually.
