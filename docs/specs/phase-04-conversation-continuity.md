# Phase 04 Specification: Conversation Continuity and Stream Replay

## Implementation Status

Status as of 2026-05-23: implemented in `services/inference-gateway` with the Phase 4 decisions recorded in [ADR-0014](../adr/ADR-0014-phase-04-conversation-continuity.md). Docker image validation and live container-backed PostgreSQL/Redis/Kafka testing remain deferred until explicitly requested.

Existing foundation:

- `POST /v1/inference/stream` starts a new backend-owned conversation.
- `POST /v1/conversations/{conversationId}/messages/stream` continues an existing conversation.
- `conversation` and `conversation_message` provide canonical persisted conversation state.
- `inference_request`, usage, error, and cancellation tables provide request lifecycle state.
- Redis tracks active stream and cancellation state.
- Kafka lifecycle events and Phase 3 ingestion provide analytics facts, but not token-level replay.

## Goals

- Provide conversation metadata and message history APIs for UI and support workflows.
- Support stream resume/replay from a known cursor.
- Provide a consistent conversation timeline across user messages, assistant messages, request lifecycle events, cancellation, and failures.
- Make reconnect semantics explicit and idempotent for clients.
- Keep canonical conversation history in PostgreSQL.
- Use Redis only for short-lived active-stream replay/cursor state.

## Scope

- Conversation read APIs:
  - list conversations by tenant/project
  - get one conversation metadata record
  - list conversation messages
  - get conversation timeline/events
- Stream replay API:
  - `GET /v1/conversations/{conversationId}/events?after={cursor}`
  - replay recent active stream events from Redis when available
  - replay canonical persisted timeline from PostgreSQL when Redis state has expired
- Durable request-level timeline events derived from existing PostgreSQL state.
- Optional short-lived Redis stream event buffer for active SSE reconnect.
- Cursor format and client deduplication rules.
- Focused tests for ordering, pagination/cursoring, ownership validation, cancellation/failure timeline entries, duplicate delivery, and Redis-expired fallback.

## Non-Goals

- No semantic memory, vector search, or long-term summarization.
- No multi-user collaborative editing.
- No arbitrary full-text search.
- No token-level durable replay unless explicitly approved.
- No analytics dashboard or ClickHouse query API.
- No authentication provider implementation; tenant/project validation remains request-field based until auth is defined.
- No container-backed PostgreSQL/Redis/Kafka integration tests unless explicitly requested.

## Assumptions

- `conversation` remains the aggregate root for conversation history.
- `conversation_message` remains the canonical durable store for user, assistant, system, and tool message content.
- `inference_request` remains the canonical durable store for request lifecycle metadata.
- Raw prompt/completion content can be returned only by conversation/message APIs that enforce tenant/project scoping and future redaction policy.
- SSE delivery is at-least-once, not exactly-once.
- Clients must deduplicate by stable event id/cursor.
- Redis replay state is best-effort and short-lived.

## Resolved Decisions

1. Phase 4 supports durable message-level and request-lifecycle replay only. Durable token/chunk replay and `inference_stream_event` remain deferred.
2. `GET /v1/conversations/{conversationId}/events` supports both JSON history pages and SSE active resume using `mode=history|stream`.
3. Public cursors are opaque server-owned strings.
4. Redis active stream replay state uses a 15 minute TTL after stream completion or disconnect.
5. Conversation list/get APIs return metadata only. Message and timeline endpoints return raw content with tenant/project scoping.
6. Concurrent active continuation streams for the same conversation are rejected.
7. Cancelled/failed partial assistant output is persisted as a message only when provider content was emitted; message metadata marks it partial and lifecycle events expose cancellation/failure state.
8. New conversation lifecycle Kafka events are deferred. Phase 4 does not publish `conversation.*` events.
9. Timeline history is derived from existing PostgreSQL tables plus the Redis active replay buffer. Phase 4 does not add `conversation_timeline_event` or `inference_stream_event`.
10. Conversation list/message/timeline pagination defaults to 50 items with a maximum of 200 items.

## Recommended Approach

- Start with message-level continuity and request-lifecycle replay, not durable token-level replay.
- Use a JSON timeline API for durable history and an SSE resume API for active stream reconnect.
- Prefer opaque cursors externally, even if internally they encode timestamp and sequence.
- Derive durable timeline from existing `conversation_message`, `inference_request`, `inference_usage`, `inference_error`, and `inference_cancellation` tables for Phase 4.
- Use Redis only as a short-lived active stream event buffer keyed by request id and conversation id.
- Keep token-level durable replay deferred unless the user explicitly approves `inference_stream_event`.
- Split metadata and raw content:
  - conversation list/get returns metadata only
  - message/timeline endpoints return message content with tenant/project scoping
- Reject concurrent active continuation streams for the same conversation in Phase 4 to avoid ambiguous message ordering.

## Proposed API Contract

The OpenAPI contract should be expanded before implementation. Existing placeholder `libs/contracts/openapi/conversation.v1.yaml` should become the Phase 4 conversation contract.

### `GET /v1/conversations`

Lists conversations for a tenant/project.

Required query parameters:

- `tenantId`
- `projectId`

Optional query parameters:

- `status`
- `limit`
- `cursor`

Response fields:

- `items`
  - `conversationId`
  - `tenantId`
  - `projectId`
  - `status`
  - `title`
  - `titleSource`
  - `createdAt`
  - `updatedAt`
  - `lastMessageAt`
  - `messageCount`
  - `activeRequestId`
- `nextCursor`

### `GET /v1/conversations/{conversationId}`

Returns conversation metadata.

Required query parameters:

- `tenantId`
- `projectId`

Response fields:

- `conversationId`
- `tenantId`
- `projectId`
- `status`
- `title`
- `titleSource`
- `createdAt`
- `updatedAt`
- `cancelledAt`
- `messageCount`
- `latestRequestStatus`

### `GET /v1/conversations/{conversationId}/messages`

Returns persisted conversation messages.

Required query parameters:

- `tenantId`
- `projectId`

Optional query parameters:

- `after`
- `limit`

Response fields:

- `items`
  - `messageId`
  - `conversationId`
  - `role`
  - `sequence`
  - `content`
  - `contentHash`
  - `estimatedTokens`
  - `redactionState`
  - `metadata`
  - `createdAt`
- `nextCursor`

### `GET /v1/conversations/{conversationId}/events`

Returns a conversation timeline after an optional cursor.

Required query parameters:

- `tenantId`
- `projectId`

Optional query parameters:

- `after`
- `limit`
- `mode`: `history` or `stream`

Recommended initial behavior:

- `mode=history`: returns JSON page of timeline events.
- `mode=stream`: returns `text/event-stream` and first replays events after `after`, then follows active stream when available.

Timeline event types:

- `conversation.created`
- `conversation.message`
- `request.accepted`
- `request.streaming`
- `request.completed`
- `request.cancelled`
- `request.failed`
- `usage.recorded`
- `heartbeat` for active stream mode only

Timeline event fields:

- `id`
- `type`
- `conversationId`
- `requestId`
- `messageId`
- `sequence`
- `occurredAt`
- `data`

### `DELETE /v1/conversations/{conversationId}/stream`

Cancels active stream work for a conversation.

Required query parameters:

- `tenantId`
- `projectId`

Request headers:

- `X-Requested-By`
- `X-Cancel-Reason`

Required behavior:

- Find the active inference request for the conversation.
- Delegate to existing request cancellation behavior.
- Return deterministic error if there is no active request.

## Event Schemas

Phase 4 defers new Kafka event schemas for:

- `conversation.created`
- `conversation.message_appended`
- `conversation.resumed`
- `conversation.cancelled`

Rules:

- Do not publish raw message content to Kafka.
- Include content hashes, message ids, role, sequence, estimated tokens, and redaction state.
- Include tenant/project/conversation correlation fields.

## Data Model Impact

### Option A: Derive Timeline From Existing Tables

Use:

- `conversation`
- `conversation_message`
- `inference_request`
- `inference_usage`
- `inference_error`
- `inference_cancellation`

Pros:

- No new durable event table.
- Lower storage/write volume.
- Uses existing canonical records.

Cons:

- Timeline reconstruction logic is more complex.
- Cannot replay token/chunk events after Redis expires.

### Option B: Add `conversation_timeline_event`

Purpose: durable, normalized event log for conversation timeline.

Candidate fields:

- `id`
- `conversation_id`
- `request_id`
- `message_id`
- `event_type`
- `sequence`
- `payload`
- `occurred_at`

Pros:

- Simple replay and cursoring.
- Stable event ids independent of table joins.

Cons:

- Duplicates information from canonical tables.
- Adds write-path complexity.

### Option C: Add `inference_stream_event`

Purpose: durable token/chunk replay.

Pros:

- Enables token-level replay after reconnect.
- Supports richer trace inspection later.

Cons:

- High write volume.
- Higher privacy and retention risk.
- Previously deferred for Phase 2 and Phase 3.

Recommended Phase 4 choice: Option A plus Redis short-lived active stream buffer.

## Redis Impact

Recommended keys:

- `conversation:{conversationId}:active-request`
- `conversation:{conversationId}:stream-events`
- `inference:{requestId}:stream-events`

Stored values:

- recent SSE/timeline events with stable event ids
- active request id
- expiration timestamp

Recommended TTL:

- 15 minutes after stream completion or disconnect.

## Observability Impact

Metrics:

- `conversation_timeline_requests_total`
- `conversation_timeline_replay_events_total`
- `conversation_timeline_redis_hits_total`
- `conversation_timeline_redis_misses_total`
- `conversation_active_stream_conflicts_total`

Trace attributes:

- `tenant.id`
- `project.id`
- `conversation.id`
- `request.id`
- `replay.mode`
- `replay.source`
- `event.count`

Logging rules:

- Do not log raw message content.
- Log conversation id, request id, event count, replay source, and cursor metadata.

## Risks

- Durable token-level replay would materially increase write volume, storage cost, and privacy review scope.
- Deriving timeline events from existing tables keeps the write path smaller, but can make ordering and cursor logic more complex.
- SSE resume is at-least-once, so clients must deduplicate by stable event id.
- Redis replay state can expire before a client reconnects; PostgreSQL fallback must be correct and predictable.
- Concurrent continuations can produce confusing message order unless Phase 4 defines and enforces one policy.
- Returning raw message content expands the API surface that needs tenant/project authorization and future redaction controls.
- Cursor formats can become hard to evolve if clients depend on internal sequence details.

## Acceptance Criteria

- OpenAPI contract for Phase 4 conversation APIs is complete and validated.
- Conversation list/get APIs enforce tenant/project scoping.
- Message history API returns ordered messages with stable cursors.
- Timeline API returns deterministic event ordering for messages, lifecycle, usage, cancellation, and failure.
- Resume from cursor does not return events before the cursor.
- Duplicate delivery is possible but every event has stable id for client dedupe.
- Redis replay path is used for active/recent stream events when available.
- PostgreSQL fallback path works when Redis replay state has expired.
- Cancelling by conversation id cancels the active inference request if one exists.
- Concurrent continuation behavior is implemented according to the approved decision.
- Raw content is never logged or published to Kafka.
- Focused service/controller tests pass.
- `npm run contracts` and `mvn -pl services/inference-gateway test` pass.

## Test Strategy

- Controller tests for list, get, messages, timeline, resume, and conversation cancellation.
- Service tests for tenant/project ownership checks.
- Cursor tests for after-cursor replay and pagination.
- Ordering tests across messages and request lifecycle records.
- Redis-hit and Redis-miss tests using fake adapters.
- Cancellation interaction tests for active and inactive conversations.
- Duplicate delivery/idempotent client event id tests.
- Contract validation for `conversation.v1.yaml`.

## Decision Gates Before Implementation

All Phase 4 decision gates are resolved in [ADR-0014](../adr/ADR-0014-phase-04-conversation-continuity.md). Implementation should stay within the approved scope above unless a new ADR updates the decision.
