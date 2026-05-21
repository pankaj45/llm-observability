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
| `inference.stream.v1` | Optional token/chunk stream events | token_streamed, usage_delta |
| `conversation.lifecycle.v1` | Conversation lifecycle events | created, resumed, archived |
| `observability.deadletter.v1` | Failed processing records | consumer failure envelopes |

Token-level stream events may be sampled or disabled by policy to control cost and privacy risk.

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
- `streaming`
- `inputMessageCount`
- `requestedParameters`
- `metadata`

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
- `firstTokenLatencyMs`

## Event: `inference.cancelled`

Purpose: records client or system-requested cancellation.

Payload fields:

- `requestId`
- `conversationId`
- `cancelledBy`
- `reason`
- `providerCancellationAttempted`
- `providerCancellationSucceeded`
- `durationMs`

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

