# Agent Instructions

This project is a spec-driven AI observability and inference logging platform.

## Required Workflow

Before implementing any feature:

- Read `README.md`.
- Read the relevant phase spec in `docs/specs/`.
- Read the architecture decisions in `docs/adr/`.
- Identify ambiguities, assumptions, tradeoffs, risks, and acceptance criteria.
- Update specs, ADRs, README, setup instructions, and architecture docs when decisions change.

Every major technical decision must create or update an ADR in `docs/adr/` using this format:

- Context
- Decision
- Alternatives Considered
- Tradeoffs
- Consequences

## Current Status

- Phase 2 inference gateway implementation exists in `services/inference-gateway`.
- Docker image validation is deferred until explicitly requested.
- Live Gemini/provider testing and container-backed PostgreSQL/Kafka/Redis integration tests are deferred until explicitly requested.

## Phase 2 Decisions

- Backend always creates `conversationId` for inference stream requests.
- `POST /v1/inference/stream` must not accept `conversationId`, not even optionally.
- Conversations are created implicitly with UUIDs and UI-ready titles.
- First real provider is Gemini.
- Redis is required for active stream and cancellation state.
- Raw prompt/completion content may be persisted in protected `conversation_message` records.
- Do not log raw prompts, completions, credentials, authorization headers, or provider secrets.
- Do not publish raw prompt/completion content to Kafka lifecycle events.
- `inference_stream_event` is deferred.

## Architecture Expectations

- Follow clean architecture and domain-driven boundaries.
- Keep domain code free of Spring, Kafka, Redis, R2DBC, WebClient, and provider infrastructure.
- Use Spring Boot 3, Java 21, WebFlux, PostgreSQL, Flyway, Redis, Kafka, OpenTelemetry, and structured logging.
- Keep provider behavior behind outbound ports/adapters.
- Keep APIs contract-first, validated, observable, and covered by tests.
- Prefer maintainability and extensibility over short code.

## Verification

Primary checks:

```text
npm run contracts
mvn -pl services/inference-gateway test
```

Do not build Docker images unless the user explicitly asks.

## Useful Context Files

- `docs/specs/phase-02-inference-gateway-mvp.md`
- `docs/adr/ADR-0011-inference-gateway-request-lifecycle-ownership.md`
- `docs/adr/ADR-0012-phase-02-gemini-conversation-content-and-redis.md`
- `docs/architecture/api-contracts.md`
- `docs/architecture/event-schemas.md`
- `docs/architecture/sequence-flows.md`
- `docs/local-development-strategy.md`
