# ADR-0004: Kafka as Domain Event Bus

## Context

Inference requests produce lifecycle events that need to be persisted, analyzed, replayed, and consumed by future alerting or evaluation workflows. Synchronous persistence in the streaming path can increase latency and reduce resilience.

## Decision

Use Kafka as the durable domain event bus for inference lifecycle, conversation lifecycle, and observability ingestion events.

## Alternatives Considered

- Direct synchronous writes only.
- PostgreSQL outbox without Kafka.
- Redis Streams.
- Cloud-provider-specific queues.

## Tradeoffs

- Kafka adds operational complexity but provides durable ordered partitions, replay, consumer groups, and backpressure isolation.
- Direct writes are simpler but tightly couple streaming latency to persistence and analytics.
- Redis Streams are simpler locally but less suitable as the primary durable integration bus for high-volume replayable analytics.
- Cloud-specific queues can reduce operations but hurt portability.

## Consequences

- Event schemas are first-class contracts and must be versioned.
- Consumers must be idempotent and replay-safe.
- Consumer lag, dead-letter counts, and publish failures become production SLO inputs.
- Partitioning strategy must preserve useful ordering, likely by tenant/project/request or conversation depending on the event type.

