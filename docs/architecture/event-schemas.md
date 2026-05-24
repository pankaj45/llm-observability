# Event Schemas

## Event Contract Policy

Events are durable integration contracts. Producers and consumers must validate schemas in CI, and schema changes must be backward compatible unless a new major version is introduced.

Planned schema location:

```text
libs/contracts/events/
```

## Topic Strategy

| Topic | Purpose | Example Events |
| --- | --- | --- |
| `inference.lifecycle.v1` | Request lifecycle events | requested, completed, cancelled, failed |
| `inference.stream.v1` | Deferred optional token/chunk stream events | token_streamed, usage_delta |
| `conversation.lifecycle.v1` | Deferred conversation lifecycle events | created, resumed, archived |
| `observability.deadletter.v1` | Failed processing records | consumer failure envelopes |

Token-level stream events and conversation lifecycle Kafka events are deferred through Phase 4. Active stream replay uses short-lived Redis state, while durable conversation timelines are derived from PostgreSQL canonical records.

## Common Envelope

```json
{
  "eventId": "uuid",
  "eventName": "inference.requested",
  "schemaVersion": "1.0.0",
  "occurredAt": "2026-05-22T00:00:00Z",
  "producer": "inference-gateway",
  "tenantId": "tenant_123",
  "projectId": "project_123",
  "correlationId": "corr_123",
  "traceparent": "00-...",
  "idempotencyKey": "tenant_123:request_123:requested",
  "payload": {}
}
```

## Event: `inference.requested`

Purpose: records that the gateway accepted an inference request.

Payload fields:

- `requestId`
- `conversationId`
- `provider`
- `model`
- `status`
- `streaming`
- `inputMessageCount`
- `inputContentHash`

## Event: `inference.token_streamed`

Purpose: records a streamed token or chunk when enabled by policy.

Payload fields:

- `requestId`
- `conversationId`
- `sequence`
- `chunkType`
- `contentHash`
- `tokenCount`
- `providerLatencyMs`

Raw token content should not be included by default. If retention policy allows token text, it must be explicitly encrypted or redacted according to tenant settings.

## Event: `inference.completed`

Purpose: records successful completion and usage metadata.

Payload fields:

- `requestId`
- `conversationId`
- `provider`
- `model`
- `finishReason`
- `inputTokens`
- `outputTokens`
- `totalTokens`
- `cost`
- `durationMs`

## Phase 2 Required Lifecycle Events

Phase 2 must implement and validate schemas for:

- `inference.requested`
- `inference.completed`
- `inference.cancelled`
- `inference.failed`

Phase 2 should not publish `inference.token_streamed` by default unless token-level persistence and stream replay are explicitly approved.

Phase 2 lifecycle events must not include raw prompt or completion content. They may include message counts, token counts, content hashes, provider/model identifiers, and timing metadata.

Conversation context compaction summaries are derived protected content. Lifecycle events must not include snapshot summary text; only hashes, counts, strategy names, and provider/model metadata are allowed if compaction metadata is added to events later.

## Phase 3 Ingestion Pipeline

Phase 3 consumes `inference.lifecycle.v1` events in `services/ingestion-worker`.

Required ingestion behavior:

- Deduplicate by `idempotencyKey` when present, falling back to `eventId`.
- Persist processing state in PostgreSQL `ingestion_processed_event`.
- Write normalized lifecycle facts to ClickHouse `inference_lifecycle_fact`.
- Mark failed analytics writes as `FAILED` in the processing ledger.
- Skip duplicate events before ClickHouse writes.

Phase 3 still defers `inference.token_streamed` consumption. Lifecycle facts must not contain raw prompt or completion content.

Required lifecycle event fields:

- `requestId`
- `conversationId`
- `provider`
- `model`
- `status`
- `occurredAt`
- `durationMs` when available
- `traceparent`
- `idempotencyKey`

## Event: `inference.cancelled`

Purpose: records client or system-requested cancellation.

Payload fields:

- `requestId`
- `conversationId`
- `provider`
- `model`
- `status`
- `reason` when provided by cancellation API
- `providerCancellationAttempted`
- `providerCancellationSucceeded`

## Event: `inference.failed`

Purpose: records failure at validation, gateway, provider, persistence, or streaming boundary.

Payload fields:

- `requestId`
- `conversationId`
- `failureStage`
- `errorCode`
- `providerErrorCode`
- `retryable`
- `durationMs`

## Compatibility Rules

- Adding optional fields is backward compatible.
- Removing fields is breaking.
- Changing field meaning is breaking.
- Changing enum values requires compatibility review.
- Consumers must ignore unknown fields.
- Producers must continue emitting required fields for the full support window.

## Idempotency and Replay

- Consumers must deduplicate by `eventId` or `idempotencyKey`.
- Request lifecycle events must be replay-safe.
- Dead-letter records must include original topic, partition, offset, event id, error code, and consumer name.
- Replays must not overwrite newer authoritative state without version or timestamp checks.
