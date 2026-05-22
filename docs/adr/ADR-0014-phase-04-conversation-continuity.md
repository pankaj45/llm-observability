# ADR-0014: Phase 4 Conversation Continuity

## Context

Phase 4 adds conversation read APIs, message history, timeline replay, and stream reconnect semantics on top of the Phase 2 inference gateway and Phase 3 ingestion pipeline. The platform already persists canonical conversation messages, request lifecycle records, usage, errors, and cancellations in PostgreSQL. Redis already coordinates active stream and cancellation state. Kafka lifecycle events support analytics ingestion, but token-level stream events were explicitly deferred in earlier phases.

The main architectural question is whether Phase 4 should introduce a durable stream-event table for token/chunk replay, a durable conversation timeline event table, or derive the conversation timeline from existing canonical records while using Redis for short-lived active reconnect.

## Decision

Phase 4 will provide message-level and request-lifecycle continuity without durable token/chunk replay. Durable timeline history will be derived from existing PostgreSQL tables: `conversation`, `conversation_message`, `inference_request`, `inference_usage`, `inference_error`, and `inference_cancellation`. Redis will hold only short-lived active stream replay state with a 15 minute TTL after stream completion or disconnect.

The conversation API will split metadata from raw content. Conversation list/get endpoints return metadata only. Message and timeline endpoints return message content only after tenant/project scoping has been validated.

`GET /v1/conversations/{conversationId}/events` will support both JSON history pages and SSE active resume through `mode=history|stream`. Public cursors are opaque server-owned strings. SSE delivery remains at-least-once, and clients must deduplicate by stable event id.

Phase 4 rejects concurrent active continuation streams for the same conversation to avoid ambiguous message ordering. Cancelled or failed partial assistant output is persisted as a message only when provider content was emitted; message metadata marks it as partial and timeline lifecycle events expose the cancellation or failure state.

Phase 4 will not add `inference_stream_event`, `conversation_timeline_event`, or new `conversation.*` Kafka events. Conversation lifecycle Kafka events remain deferred until a future analytics or UI phase needs them.

## Alternatives Considered

- Add `inference_stream_event` for durable token/chunk replay.
- Add `conversation_timeline_event` for a normalized durable timeline log.
- Support only JSON history pages and no SSE resume endpoint.
- Allow concurrent active continuation streams for the same conversation.
- Publish new `conversation.created`, `conversation.message_appended`, `conversation.resumed`, and `conversation.cancelled` Kafka events in Phase 4.

## Tradeoffs

- Deriving timeline history from canonical tables keeps write volume and storage lower, but makes ordering and cursor logic more deliberate.
- Deferring durable token/chunk replay means clients cannot replay exact token deltas after Redis expires.
- Redis active replay enables short reconnect windows without introducing high-volume durable content persistence.
- Opaque cursors preserve server flexibility, but clients cannot infer sequence details.
- Rejecting concurrent continuations simplifies ordering and user-visible behavior, but limits advanced multi-turn parallel workflows.
- Deferring `conversation.*` Kafka events avoids event-contract churn before analytics consumers need them, but Phase 4 analytics will not receive conversation-specific event facts.

## Consequences

- Phase 4 implementation must not create `inference_stream_event` or `conversation_timeline_event`.
- Conversation timeline services must compose deterministic events from existing repositories.
- Redis replay adapters must treat active stream replay as best-effort and expire state after 15 minutes.
- OpenAPI contracts must document opaque cursors, default limit `50`, maximum limit `200`, JSON history, SSE stream mode, at-least-once delivery, and client deduplication.
- Tests must cover PostgreSQL fallback when Redis replay state is unavailable.
- Future durable token-level replay requires a new ADR or an update to this ADR.
