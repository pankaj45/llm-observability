# ADR-0018: Context Orchestrator and Live Data Grounding

## Context

The chatbot can answer freshness-sensitive questions from stale model knowledge because the
inference gateway currently forwards conversation messages directly to the provider without
runtime date/time context, live data tools, source provenance, or tool invocation auditability.
Questions about current markets, news, laws, public figures, software releases, schedules,
weather, and other volatile facts require backend-owned grounding before final generation.

The platform already uses clean architecture boundaries and provider adapters. The new design
must preserve those boundaries while keeping tool execution observable and controllable.

## Decision

Add a Context Orchestrator inside `services/inference-gateway` application flow. The
orchestrator injects backend-generated runtime context into every provider request and uses a
deterministic tool router to identify freshness-sensitive prompts.

Phase 08 uses platform-mediated tools only. Provider-native tools remain deferred so tool
policy, caching, rate limits, and audit metadata stay provider-neutral.

Initial tools:

- `MarketDataPort` backed by CoinGecko for crypto market data.
- `WebSearchPort` backed by Tavily for source-backed web search.

The orchestrator normalizes tool output into evidence, emits SSE tool progress/source events,
uses Redis for short-lived evidence caching, and persists metadata-only tool invocation rows in
PostgreSQL. If required tools are unavailable or fail, the orchestrator adds a stale-answer
warning to provider context rather than inventing current facts.

Client-side `groundingMode=disabled` is not introduced in production behavior for this phase.
Tool execution is governed by backend configuration and future tenant policy.

## Alternatives Considered

- Use provider-native tools immediately.
- Let the browser choose and execute tools.
- Add a separate context-service before the first implementation.
- Persist raw evidence bodies in PostgreSQL.
- Emit tool invocation events to Kafka and ClickHouse in Phase 08.
- Rely only on a stronger system prompt without external tools.

## Tradeoffs

- Platform-mediated tools add more application code than provider-native tools, but preserve
  auditability, provider portability, and consistent safety policy.
- Deterministic routing is less flexible than model-assisted planning, but easier to test and
  safer for the first production pass.
- Metadata-only ledger rows reduce privacy risk, but make full answer reconstruction depend on
  upstream source availability unless raw evidence retention is later approved.
- Tool calls delay provider token streaming, but SSE progress events keep the UI responsive.
- CoinGecko and Tavily are practical first adapters, but broader market/news coverage will need
  additional providers.

## Consequences

- `services/inference-gateway` owns context orchestration during stream preparation.
- Tool integrations live behind outbound ports and adapters.
- Runtime date/time context is injected for every provider request.
- Freshness-sensitive prompts can produce `tool.plan`, `tool.started`, `tool.completed`,
  `tool.failed`, and `source.available` SSE events before model tokens.
- Tool metadata is stored in PostgreSQL without raw prompts, raw tool bodies, credentials, or
  provider secrets.
- Redis caches short-lived evidence for repeated freshness lookups.
- Future provider-native tools, raw evidence retention, Kafka tool events, and a standalone
  context-service require ADR updates if they change ownership or persistence policy.
