# ADR-0021: Context Compaction and Provider Context Assembly

## Context

Conversation continuation previously replayed every persisted conversation message into the provider context. That is correct for short conversations, but long conversations can approach or exceed model context windows, increase latency and cost, and make future model routing harder. The platform must keep canonical conversation history available for UI, audit, and support workflows while sending a bounded provider context for inference.

## Decision

Add an application-layer provider context assembly step before runtime/tool context orchestration. The assembler estimates provider-bound input tokens on every inference request. If the exact conversation context is below the configured threshold, it passes messages through unchanged. If it exceeds the threshold, it builds provider context from a protected persisted `conversation_context_snapshot` summary plus exact recent messages.

Snapshots are derived conversation content. They are stored in PostgreSQL, scoped through the parent conversation, and protected by the same no-logs/no-Kafka/no-ClickHouse raw-content policy as `conversation_message`. Canonical `conversation_message` records are never mutated or replaced by compaction.

The compaction strategy is `ROLLING_SUMMARY_V1`: keep the newest exact turns, compact only older contiguous messages with an LLM-backed `ConversationCompactionPort`, reuse a recent snapshot while the newly aged-out range is small, and create a new snapshot only when the threshold requires it. Runtime system context and live tool evidence are still added by the existing context orchestrator after conversation compaction. If LLM compaction fails or returns an empty summary, the gateway sends only the configured recent exact messages to the provider for that request and does not persist a new snapshot.

## Alternatives Considered

- Continue replaying every persisted message for all continuation requests.
- Delete or rewrite old conversation messages after summary generation.
- Store summaries as synthetic `conversation_message` rows.
- Perform compaction inside provider adapters.
- Publish compaction summaries to Kafka for analytics.
- Use heuristic truncation instead of an LLM-backed summary.
- Run only background/asynchronous compaction after completion.

## Tradeoffs

- Checking token budgets on every provider-bound request is cheap and keeps behavior deterministic, but it adds another application service to the request path.
- Synchronous compaction guarantees the current request can fit a bounded context, but it can add latency when a new snapshot is required.
- Persisting snapshots improves reuse and observability of compaction decisions, but derived content must be protected like raw conversation content.
- Keeping summaries separate from `conversation_message` preserves canonical history, but conversation APIs need to treat snapshots as internal provider-context artifacts unless a future admin/debug API is explicitly approved.
- LLM-backed compaction preserves semantic state better than truncation, but it adds a nested provider call, latency, cost, and another provider-failure path.
- The recent-message fallback keeps inference available when compaction fails, but older context may be omitted for that request.

## Consequences

- The inference gateway owns a new PostgreSQL table, `conversation_context_snapshot`.
- Provider context assembly runs before `ContextOrchestrator.orchestrate`.
- Small conversations are not compacted; the assembler only compacts when estimated input exceeds the configured threshold.
- Kafka lifecycle events continue to include hashes, counts, provider/model metadata, and timing only; they must not include summary text.
- Analytics APIs and ClickHouse continue to exclude raw and derived conversation content.
- Tests must cover exact passthrough, threshold-triggered compaction, latest-turn preservation, and snapshot reuse.
