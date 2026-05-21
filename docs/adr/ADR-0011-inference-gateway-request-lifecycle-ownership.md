# ADR-0011: Inference Gateway Request Lifecycle Ownership

## Context

Phase 2 introduces the first production inference workflow. The gateway must validate requests, stream provider responses, support cancellation, expose request status, emit lifecycle events, and provide enough state for idempotency and operational reliability. Persistence boundaries must be clear before Flyway migrations are written.

## Decision

The inference gateway owns the authoritative lifecycle state for active and recently completed inference requests in PostgreSQL. It publishes lifecycle events to Kafka for downstream ingestion and analytics, but Kafka consumers are not the source of truth for request status in phase 2. The final PostgreSQL entity set is approved in the phase 2 spec before migrations are implemented.

## Alternatives Considered

- Keep request lifecycle entirely in memory and publish events only.
- Make Kafka the only source of truth for request lifecycle state.
- Let the ingestion worker own all request persistence.
- Persist every streamed token and SSE event in PostgreSQL during phase 2.

## Tradeoffs

- Gateway-owned lifecycle state improves idempotency, cancellation, and status lookup, but it adds persistence work to the gateway.
- Kafka remains valuable for decoupled ingestion and replay, but downstream consumers can lag without breaking request status APIs.
- Deferring token-level stream persistence reduces write volume and privacy risk, but limits replay and reconnect capabilities in phase 2.
- Finalizing the entity set before implementation prevents unreviewed schema decisions from becoming durable.

## Consequences

- The gateway needs reactive PostgreSQL repositories and Flyway migrations after entity approval.
- The approved phase 2 migrations include provider/model catalog, conversation, conversation message, inference request, usage, error, and cancellation entities.
- Lifecycle events must include request identifiers and idempotency keys so downstream consumers can reconcile with gateway state.
- Cancellation behavior must update gateway-owned state even when provider cancellation is best-effort.
- Request status APIs can be implemented before ClickHouse analytics ingestion exists.
