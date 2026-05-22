# Phase 03 Specification: Inference Logging Pipeline

## Implementation Status

Status as of 2026-05-23: implemented as an ingestion-worker MVP for inference lifecycle events, excluding live Kafka/PostgreSQL/ClickHouse container integration verification.

Implemented:

- Kafka consumer boundary for `inference.lifecycle.v1`, disabled by default for local tests.
- Lifecycle event envelope parsing.
- Replay-safe event deduplication through PostgreSQL `ingestion_processed_event`.
- ClickHouse HTTP sink for `inference_lifecycle_fact`, with no-op sink when ClickHouse ingestion is disabled.
- Focused unit tests for parsing, dedupe, analytics fact mapping, duplicate skipping, and failure marking.
- ClickHouse schema DDL under `infra/migrations/clickhouse`.

Deferred from runtime verification:

- Live Kafka consumer execution against a running broker.
- Container-backed PostgreSQL and ClickHouse integration tests.
- Token-level stream ingestion.

## Goals

- Consume durable inference lifecycle events from Kafka.
- Deduplicate replayed or duplicate events before analytics writes.
- Reprocess events whose prior processing status is `FAILED`; skip events already marked `PROCESSED`.
- Persist processing state in PostgreSQL for auditability and retry diagnosis.
- Write normalized lifecycle facts to ClickHouse for future analytics/query APIs.
- Keep ingestion separate from the inference gateway's authoritative request lifecycle state.

## Scope

- `services/ingestion-worker` lifecycle-event ingestion application logic.
- Kafka consumer for `inference.lifecycle.v1`.
- PostgreSQL migration for ingestion processing state.
- ClickHouse lifecycle fact schema and HTTP sink.
- Unit and application-context tests.
- Documentation and ADR updates for Phase 3 pipeline ownership.

## Non-Goals

- No token-level `inference.token_streamed` ingestion.
- No ClickHouse query API implementation.
- No dashboard implementation.
- No full-text search.
- No archival/cold-storage workflow.
- No container-backed integration tests unless explicitly requested.

## Assumptions

- The inference gateway remains the source of truth for active and recent request lifecycle state in PostgreSQL.
- Kafka lifecycle events are replayable and may be delivered more than once.
- `idempotencyKey` is the preferred dedupe key; `eventId` is the fallback.
- ClickHouse stores analytics facts derived from lifecycle events, not raw prompt or completion content.
- Raw prompt/completion content remains in protected `conversation_message` rows and is not published to Kafka lifecycle events.

## Ambiguities

- Whether failed ingestion records should be automatically retried or retried by operator-driven replay.
- ClickHouse retention, partitioning, and compression policies are not final.
- Token-level telemetry sampling and persistence policy is still undecided.
- Dead-letter topic shape is documented but not implemented in this MVP.

## API Contract

No new HTTP API is introduced in Phase 3.

## Event Contracts

Consumed topic:

- `inference.lifecycle.v1`

Consumed event names:

- `inference.requested`
- `inference.completed`
- `inference.cancelled`
- `inference.failed`

Rejected/deferred event names:

- `inference.token_streamed`
- conversation lifecycle events

## Data Model Impact

### PostgreSQL: `ingestion_processed_event`

Purpose: replay-safe processing ledger for lifecycle events.

Core fields:

- `event_id`
- `dedupe_key`
- `event_name`
- `schema_version`
- `tenant_id`
- `project_id`
- `correlation_id`
- `source_topic`
- `source_partition`
- `source_offset`
- `processing_status`
- `failure_message`
- `received_at`
- `processed_at`

Rationale:

- Prevents duplicate Kafka events from creating duplicate analytics facts.
- Provides auditability for failed or skipped ingestion attempts.
- Keeps ingestion pipeline state separate from inference gateway lifecycle ownership.

### ClickHouse: `inference_lifecycle_fact`

Purpose: append-oriented analytics fact table for request lifecycle events.

Core dimensions:

- `event_id`
- `event_name`
- `tenant_id`
- `project_id`
- `request_id`
- `conversation_id`
- `provider`
- `model`
- `status`
- `occurred_at`

Core measures:

- `input_message_count`
- `input_tokens`
- `output_tokens`
- `total_tokens`
- `duration_ms`

Error/cancellation fields:

- `failure_stage`
- `error_code`
- `provider_error_code`
- `retryable`
- `cancellation_reason`
- `provider_cancellation_attempted`
- `provider_cancellation_succeeded`

## Observability Impact

- Ingestion emits counters for processed lifecycle events.
- Duplicate events increment `ingestion_lifecycle_events_duplicates_total`.
- Application health and Prometheus endpoints remain available.
- Kafka topic, partition, offset, event id, and dedupe key are persisted in processing state.

## Acceptance Criteria

- Duplicate lifecycle events do not write duplicate analytics facts.
- Failed analytics writes mark the event as `FAILED` in PostgreSQL.
- Failed processing records can be retried by replaying the lifecycle event.
- Lifecycle events map to a normalized ClickHouse fact representation.
- Kafka listener is configurable and disabled by default for local unit tests.
- PostgreSQL and ClickHouse schemas are documented.
- Docker Compose can enable the worker against Kafka, PostgreSQL, and ClickHouse.
- `mvn -pl services/ingestion-worker test` passes.

## Risks

- Without container-backed tests, live broker/database wiring still needs explicit validation.
- ClickHouse HTTP insert behavior can differ from unit assumptions under authentication or schema drift.
- The current implementation is one-event-at-a-time at the Kafka boundary; high-throughput batching should be added before production-scale load.
