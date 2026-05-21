# Architecture Overview

## System Intent

The platform observes, logs, streams, and analyzes LLM inference activity across multiple providers. It must support real-time inference streaming, cancellation, resumable conversations, durable logging, high-volume analytics, and operational observability.

## Ambiguities

- Exact tenant hierarchy is undecided.
- Identity provider is undecided.
- First supported LLM provider is undecided.
- Prompt/completion retention and redaction rules are undecided.
- Cloud provider and managed service choices are undecided.

## Assumptions

- Every request belongs to a tenant and project.
- Conversation, request, provider, and model identifiers are first-class correlation dimensions.
- Provider-specific APIs are normalized through adapters.
- PostgreSQL stores authoritative business state.
- ClickHouse stores high-volume analytics facts.
- Redis stores short-lived stream, cancellation, resume, and cache state.
- Kafka transports durable domain and integration events.

## Bounded Contexts

| Context | Responsibility |
| --- | --- |
| Inference | Request validation, provider invocation, streaming, cancellation |
| Conversation | Conversation metadata, history, resume cursors |
| Observability Ingestion | Event consumption, idempotency, persistence |
| Analytics | Querying ClickHouse/PostgreSQL for dashboards and traces |
| Provider Management | Provider configuration, model catalog, credentials policy |
| Operations | Metrics, traces, logs, health, alerts, runbooks |

## Core Runtime Flow

1. Client sends an inference request to the inference gateway.
2. Gateway validates tenant, project, model, provider, and payload.
3. Gateway creates an inference request record or emits a request event.
4. Gateway invokes the selected provider adapter.
5. Gateway streams normalized SSE events to the client.
6. Gateway emits Kafka lifecycle events.
7. Ingestion worker persists authoritative state in PostgreSQL and analytics facts in ClickHouse.
8. Analytics query service serves dashboard and trace exploration APIs.

## Architecture Documents

- [System Context](system-context.md)
- [Container Diagram](container-diagram.md)
- [Sequence Flows](sequence-flows.md)
- [API Contracts](api-contracts.md)
- [Event Schemas](event-schemas.md)
- [Repository Structure](../repository-structure.md)
- [Deployment Strategy](../deployment-strategy.md)
- [Observability Strategy](../observability-strategy.md)

## Tradeoffs

- Reactive WebFlux and SSE increase implementation complexity, but they match streaming inference and cancellation requirements better than blocking MVC.
- Kafka adds operational complexity, but it creates durable decoupling between request handling, persistence, analytics, and future alerting.
- ClickHouse adds a second database, but PostgreSQL alone is not a good fit for token-level or high-cardinality analytics at scale.
- A monorepo requires disciplined boundaries, but it simplifies contract governance and early platform evolution.

