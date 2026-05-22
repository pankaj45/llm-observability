# ADR-0013: Phase 3 Ingestion Pipeline Ownership

## Context

Phase 3 introduces the durable ingestion pipeline for inference lifecycle events. Phase 2 already made the inference gateway the authoritative owner of active and recently completed request lifecycle state in PostgreSQL. The new ingestion worker must consume Kafka events without creating a competing source of truth, while still supporting replay-safe analytics persistence.

## Decision

The ingestion worker consumes `inference.lifecycle.v1` events and writes processing state to PostgreSQL plus normalized lifecycle facts to ClickHouse. PostgreSQL `ingestion_processed_event` is a processing ledger for idempotency, replay audit, source offsets, and failure diagnosis; it is not the authoritative inference request table. ClickHouse `inference_lifecycle_fact` is the analytics destination for request lifecycle events. The ingestion worker deduplicates by lifecycle event `idempotencyKey` when present and falls back to `eventId`. Replayed events already marked `PROCESSED` are skipped, while events marked `FAILED` can be retried by replaying the same dedupe key.

Token-level stream ingestion remains deferred. The ClickHouse sink is configurable and disabled by default in local tests; Docker Compose can enable it with Kafka and ClickHouse.

## Alternatives Considered

- Make the ingestion worker the authoritative owner of all inference request state.
- Store lifecycle analytics only in PostgreSQL.
- Write directly from the inference gateway to ClickHouse.
- Persist token-level stream events in Phase 3.
- Deduplicate only by Kafka topic/partition/offset.

## Tradeoffs

- Keeping gateway-owned request state avoids split-brain lifecycle ownership, but ingestion must reconcile analytics from events rather than owning request transitions.
- A PostgreSQL processing ledger adds operational state, but gives replay safety and failure diagnostics.
- ClickHouse is better suited for analytics scans than PostgreSQL, but adds schema and runtime dependency surface.
- Deferring token-level ingestion reduces privacy and write-volume risk, but Phase 3 analytics cannot replay token streams or answer token-chunk questions.
- Deduplicating by `idempotencyKey` makes producer retries safe across Kafka offsets, but depends on producers emitting stable keys.

## Consequences

- `services/ingestion-worker` owns lifecycle event consumption and analytics fact writes.
- `ingestion_processed_event` is required before live Kafka consumption is enabled.
- `inference_lifecycle_fact` must exist in ClickHouse before ClickHouse ingestion is enabled.
- Failed analytics writes mark the processing ledger row as `FAILED`.
- Failed ledger rows can be retried on replay.
- Duplicate events are skipped before analytics writes.
- Container-backed Kafka/PostgreSQL/ClickHouse verification remains a separate explicit validation pass.
