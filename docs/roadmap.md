# Project Roadmap and Milestone Plan

## Purpose

This roadmap defines the staged delivery plan for a production-grade AI observability and inference logging platform. It is intentionally implementation-aware but code-free: each milestone must create or update specs, ADRs, architecture docs, API contracts, event schemas, tests, setup instructions, and deployment documentation before application code is merged.

## Ambiguities

- Tenancy model: single-tenant, workspace-based multi-tenant, or organization/project hierarchy.
- Authentication and authorization provider: built-in auth, OIDC/SAML, or delegated enterprise identity.
- Provider priority: OpenAI, Anthropic, local models, Azure OpenAI, Bedrock, Vertex AI, or all at once.
- Compliance boundary: retention, PII handling, encryption, redaction, and audit requirements are not yet defined.
- Sampling policy: whether every token/request is persisted or sampled by tenant, environment, or model.
- Billing use case: whether cost attribution is informational or used for chargeback.
- Query latency targets: dashboard and trace exploration SLOs need product-level confirmation.
- Data residency: regional deployment requirements are unknown.

## Recommended Initial Assumptions

- Start with workspace/project based multi-tenancy using explicit tenant and project identifiers on all persisted records and events.
- Use OIDC-compatible authentication as the long-term direction, with local development using test identities.
- Implement provider abstraction early, but ship the first provider adapter behind the same interface.
- Persist authoritative conversation and request metadata in PostgreSQL, high-volume inference telemetry in ClickHouse, and short-lived streaming/cancellation state in Redis.
- Treat Kafka as the integration backbone for durable observability events and asynchronous analytics ingestion.
- Make all services observable from their first commit with OpenTelemetry, Prometheus metrics, structured logs, and health probes.

## Major Architecture Decisions

Initial decisions are recorded as ADRs:

- [ADR-0001: Spec-Driven Monorepo Governance](adr/ADR-0001-spec-driven-monorepo-governance.md)
- [ADR-0002: Clean Architecture and Domain Boundaries](adr/ADR-0002-clean-architecture-domain-boundaries.md)
- [ADR-0003: Reactive WebFlux and SSE Streaming](adr/ADR-0003-reactive-webflux-sse-streaming.md)
- [ADR-0004: Kafka as Domain Event Bus](adr/ADR-0004-kafka-domain-event-bus.md)
- [ADR-0005: PostgreSQL, ClickHouse, and Redis Persistence Split](adr/ADR-0005-postgresql-clickhouse-redis-persistence.md)
- [ADR-0006: OpenTelemetry, Prometheus, and Grafana Observability](adr/ADR-0006-opentelemetry-prometheus-grafana.md)
- [ADR-0007: Docker Compose, Kubernetes, and Helm Deployment](adr/ADR-0007-docker-compose-kubernetes-helm.md)
- [ADR-0008: Multi-Provider LLM Adapter Model](adr/ADR-0008-multi-provider-llm-adapter-model.md)
- [ADR-0009: Maven and npm Workspace Bootstrap](adr/ADR-0009-maven-npm-workspace-bootstrap.md)
- [ADR-0010: R2DBC Repositories with Flyway Migrations](adr/ADR-0010-r2dbc-repositories-with-flyway-migrations.md)
- [ADR-0011: Inference Gateway Request Lifecycle Ownership](adr/ADR-0011-inference-gateway-request-lifecycle-ownership.md)
- [ADR-0012: Phase 2 Gemini, Conversation Content, and Redis Decisions](adr/ADR-0012-phase-02-gemini-conversation-content-and-redis.md)

## Milestones

| Milestone | Name | Primary Outcome | Exit Gate |
| --- | --- | --- | --- |
| M0 | Specification foundation | Architecture and delivery baseline | Docs, ADRs, diagrams, and standards exist |
| M1 | Platform bootstrap | Buildable monorepo, CI, contract-first skeleton | Contracts compile and CI runs |
| M2 | Inference gateway MVP | Streaming inference API with one provider | SSE, cancellation, traces, tests |
| M3 | Inference logging pipeline | Durable event ingestion and persistence | PostgreSQL and ClickHouse records verified |
| M4 | Conversation continuity | Resumable conversations and replay support | Resume APIs and consistency tests pass |
| M5 | Analytics and dashboards | Query APIs and operator UI | Dashboard p95 and correctness gates pass |
| M6 | Production hardening | Security, scale, reliability, deployment | SLOs, Helm, alerts, runbooks complete |

## Phase 0: Specification Foundation

### Goals

- Establish project roadmap, architecture documentation, ADRs, diagrams, engineering standards, local development strategy, deployment strategy, and observability strategy.
- Define future repository structure without implementing application code.
- Capture assumptions, ambiguities, and tradeoffs before implementation.

### Scope

- Documentation under `docs`.
- Root README updates.
- Initial ADRs for foundational decisions.
- C4-style diagrams and high-level sequence flows.

### Non-Goals

- No frontend or backend implementation.
- No Docker, Kubernetes, or Helm manifests yet.
- No generated OpenAPI files yet; only contract inventory and conventions.

### Acceptance Criteria

- README links to all foundational docs.
- ADRs exist for the major architectural decisions.
- Roadmap defines phased delivery with goals, scope, non-goals, acceptance criteria, and risks.
- Diagrams document system context, containers, deployment, and sequence flows.
- API and event contract conventions are documented.

### Risks

- Some product assumptions may change once authentication, tenancy, compliance, and provider priorities are clarified.
- Documentation can become stale unless future phases update it as part of their acceptance criteria.

## Phase 1: Platform Bootstrap

Detailed phase spec: [Phase 01: Platform Bootstrap](specs/phase-01-platform-bootstrap.md).

### Goals

- Create the monorepo structure for frontend, backend services, shared contracts, infrastructure, and tests.
- Add baseline CI, formatting, linting, dependency management, and local development commands.
- Introduce contract-first OpenAPI and event schema directories.
- Bootstrap Spring Boot WebFlux service shells for the planned backend containers.

### Scope

- Root build orchestration and CI configuration.
- Empty but buildable Next.js and Spring Boot service shells.
- Dockerfiles for every created service.
- Health and metrics endpoints for service shells.
- Test containers or equivalent integration test foundation.
- PostgreSQL R2DBC and Flyway dependencies wired for future domain migrations.
- OpenTelemetry, Prometheus, and structured-log baseline configuration.

### Non-Goals

- No business workflow implementation.
- No real provider integration.
- No production dashboards beyond baseline service health.

### Acceptance Criteria

- CI runs frontend lint/type checks, backend compile/tests, contract validation, and schema validation.
- Each service has Dockerfile, health endpoint, metrics endpoint, structured logging baseline, and OpenTelemetry wiring.
- README and setup docs include local bootstrap instructions.
- ADRs are updated for any build or framework decisions not covered by existing ADRs.

### Risks

- Multi-language dependency management can slow CI if not cached carefully.
- Premature service proliferation can create operational cost before boundaries are validated.

## Phase 2: Inference Gateway MVP

Detailed phase spec: [Phase 02: Inference Gateway MVP](specs/phase-02-inference-gateway-mvp.md).

Status as of 2026-05-22: implemented in `services/inference-gateway` with module tests and contract validation. Docker image validation and live/container integration tests are deferred to the agreed later image-testing pass.

### Goals

- Deliver the first production-grade streaming inference API.
- Support one provider through the multi-provider adapter interface.
- Emit normalized lifecycle events for request acceptance, completion, failure, and cancellation.
- Persist approved request lifecycle state for idempotency, status lookup, and cancellation.

### Scope

- Spring WebFlux inference gateway.
- SSE streaming endpoint.
- Request validation, structured errors, and OpenAPI contract.
- Provider adapter interface and first implementation.
- Cancellation using request-scoped state and provider cancellation where available.
- Unit, contract, integration, and streaming behavior tests.
- PostgreSQL entity set approval before Flyway migrations are written.

### Non-Goals

- No advanced analytics UI.
- No provider routing optimization.
- No fine-grained billing workflows.

### Acceptance Criteria

- API supports streaming, cancellation, and deterministic error responses.
- OpenAPI spec is validated and covered by contract tests.
- Kafka events are emitted for request lifecycle transitions.
- Traces include tenant, project, conversation, request, provider, and model attributes with cardinality controls.
- Final database entities are approved before implementation. Phase 2 approval selects Gemini, implicit conversation creation, Redis, and message persistence.
- README, architecture docs, and setup instructions are updated.

### Risks

- Provider streaming semantics differ and may require adapter-specific normalization.
- Cancellation semantics may be best-effort for some providers.

## Phase 3: Inference Logging Pipeline

Detailed phase spec: [Phase 03: Inference Logging Pipeline](specs/phase-03-inference-logging-pipeline.md).

Status as of 2026-05-23: implemented as an ingestion-worker MVP for inference lifecycle events, with live Kafka/PostgreSQL/ClickHouse container verification deferred until explicitly requested.

### Goals

- Persist request metadata, conversation state, and high-volume telemetry through a durable pipeline.
- Separate OLTP correctness from analytics throughput.

### Scope

- Kafka consumers for inference lifecycle events.
- PostgreSQL schema for tenants, projects, conversations, requests, and provider metadata.
- ClickHouse schema for token, latency, cost, error, and evaluation telemetry.
- Idempotency keys and replay-safe event handling.
- Migration strategy and integration tests.

### Non-Goals

- No full text search unless explicitly specified.
- No long-term archival or cold storage.

### Acceptance Criteria

- Duplicate Kafka events do not create duplicate authoritative records.
- ClickHouse ingestion supports batched writes and backpressure.
- PostgreSQL and ClickHouse schemas are documented.
- Failure and retry behavior is tested.
- Data lineage from API request to analytics row is traceable.

### Risks

- Token-level persistence may generate high write volume and storage cost.
- Schema evolution can break historical analytics without versioning discipline.

## Phase 4: Conversation Continuity

Detailed phase spec: [Phase 04: Conversation Continuity and Stream Replay](specs/phase-04-conversation-continuity.md).

Status as of 2026-05-23: implemented in `services/inference-gateway` with conversation metadata, message history, derived timeline, active replay, conversation cancellation, contract validation, and focused service/controller tests. Durable token-level replay remains deferred by ADR-0014.

### Goals

- Support resumable conversations and stream reconnection after client disconnects.
- Provide a consistent conversation timeline across provider calls and user messages.

### Scope

- Conversation APIs.
- Resume endpoint and event replay semantics.
- Redis-backed short-lived cursor/session state.
- PostgreSQL-backed canonical conversation history.
- Consistency tests for reconnect, cancellation, and duplicate delivery.

### Non-Goals

- No collaborative multi-user editing.
- No semantic memory or vector search unless separately specified.

### Acceptance Criteria

- Clients can resume from a known event cursor.
- Conversation history is eventually complete after streaming interruptions.
- Resume semantics are documented in OpenAPI and sequence diagrams.
- Cancellation and resume interactions are tested.

### Risks

- Exactly-once delivery is not realistic over SSE; clients need idempotent event handling.
- Long-lived streams can pressure gateway resources if not bounded.

## Phase 5: Analytics and Operator UI

Detailed phase spec: [Phase 05: Analytics and Operator UI](specs/phase-05-analytics-and-operator-ui.md).

Status as of 2026-05-23: approved for implementation with ClickHouse-backed analytics query APIs and a Next.js operator dashboard. Live container-backed analytics validation remains deferred until explicitly requested.

### Goals

- Provide operator-facing visibility into requests, latency, cost, error rates, providers, models, and conversation traces.
- Deliver a usable Next.js + Mantine frontend grounded in real query APIs.

### Scope

- Analytics query service.
- Dashboard APIs.
- Frontend views for request exploration, conversation trace, provider/model comparison, and error drilldown.
- Query performance tests and UI integration tests.

### Non-Goals

- No self-service billing portal.
- No arbitrary SQL workbench exposed to end users.

### Acceptance Criteria

- Dashboard queries meet agreed p95 latency targets on seeded load-test data.
- Frontend handles loading, empty, error, and degraded states.
- User actions are traced from browser to backend query.
- Docs include frontend architecture and UX assumptions.

### Risks

- Analytics cardinality can make dashboards slow or expensive.
- Product scope can drift from observability into general BI.

## Phase 6: Production Hardening

### Goals

- Make the platform deployable, operable, and supportable in production environments.
- Add security controls, SLOs, alerts, runbooks, and resilience testing.

### Scope

- Helm charts and environment overlays.
- Kubernetes health, readiness, startup probes, resource requests, limits, autoscaling, and disruption budgets.
- Secrets strategy.
- Authentication and authorization integration.
- SLO dashboards and alert rules.
- Load, chaos, and failure-mode tests.

### Non-Goals

- No marketplace or public SaaS onboarding until compliance and tenant isolation are validated.
- No custom cloud provisioning tool unless needed.

### Acceptance Criteria

- Platform deploys through Helm to a Kubernetes environment.
- SLOs, alerts, and runbooks cover critical user journeys.
- Security review findings are resolved or explicitly accepted.
- Backups, restore procedures, and migration rollback plans are tested.

### Risks

- Kubernetes readiness depends on environment-specific ingress, identity, storage, and secret management.
- Compliance requirements may require additional controls such as data classification, audit exports, or regional isolation.
