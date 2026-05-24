# LLM Observability Platform

A production-grade AI observability and inference logging platform with a streaming chatbot UI, live-data grounding via context orchestration, regex-based PII redaction, event-driven ingestion pipeline, multi-turn conversation continuity, and an operator analytics dashboard.

## Quick Start

```bash
# 1. Install Node dependencies
npm install

# 2. Create a Compose env file beside the Docker Compose file
cat > infra/docker-compose/.env <<'EOF'
GEMINI_API_KEY=your_gemini_key_here
OPENAI_API_KEY=your_openai_key_here
# Optional OpenAI account scoping
OPENAI_ORGANIZATION=
OPENAI_PROJECT=
# Context grounding tools (Phase 8)
TAVILY_API_KEY=your_tavily_key_here
COINGECKO_API_KEY=your_coingecko_key_here
EOF

# 3a. Start in the background — blocks until every service is healthy, then prints URLs
make dev-ready

# 3b. Or stream logs to the terminal (classic)
make dev
# In a second terminal, run this to know when everything is up:
make ready
```

When the platform is ready you will see:

```
╔══════════════════════════════════════════════════════════════╗
║         ✅  Platform is ready!                               ║
╚══════════════════════════════════════════════════════════════╝

  Chatbot UI         →  http://localhost:3000
  Analytics Dashboard→  http://localhost:3000/analytics
  Grafana            →  http://localhost:3001   (admin / admin)
  Prometheus         →  http://localhost:9090
  inference-gateway  →  http://localhost:8080/actuator/health
  analytics-query    →  http://localhost:8081/actuator/health
  ingestion-worker   →  http://localhost:8082/actuator/health
```

> Kafka, PostgreSQL, Redis, ClickHouse, Prometheus, Grafana, and all services start via Docker Compose.
> Compose also initializes the ClickHouse analytics schema used by `/analytics` and wires ClickHouse credentials into analytics services.
> Provider credentials are read from `infra/docker-compose/.env`, the folder that contains `docker-compose.yml`.

### Prerequisites

- Docker and Docker Compose
- Node.js ≥ 18
- Java 21 + Maven (only needed to run backend tests locally)

### Useful commands

| Command | Description |
|---|---|
| `make dev-ready` | Start full stack detached, block until healthy, print URL banner |
| `make dev` | Start full stack with logs streaming to the terminal |
| `make ready` | Poll health endpoints in a second terminal after `make dev` |
| `make down` | Stop all services |
| `make test` | Run contracts + backend + frontend checks |
| `make contracts` | Validate OpenAPI and event schemas |
| `make test-backend` | Run all Maven service tests |
| `make test-frontend` | TypeScript typecheck |

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│  Browser (Next.js, port 3000)                                       │
│  /          → Chatbot UI (SSE streaming, multi-turn conversations)  │
│  /analytics → Operator Dashboard (latency, throughput, errors)      │
└──────────────┬──────────────────────────────────────────────────────┘
               │ HTTP/SSE
               ▼
┌─────────────────────────────────────────────────────────────────┐
│  inference-gateway  :8080                   Spring Boot/WebFlux  │
│                                                                  │
│  POST /v1/inference/stream                                       │
│  POST /v1/conversations/{id}/messages/stream                     │
│  DELETE /v1/inference/{requestId}/stream  (cancel)              │
│  GET  /v1/inference/{requestId}           (status)              │
│  GET  /v1/conversations/…                 (list/get/messages)   │
│  GET  /v1/models                          (model catalog)       │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ Context Orchestrator                                      │   │
│  │  RuntimeContextPolicy → injects date/time/timezone        │   │
│  │  ToolNeedRouter       → deterministic freshness routing   │   │
│  │  MarketDataPort       ← CoinGecko adapter                │   │
│  │  WebSearchPort        ← Tavily adapter                   │   │
│  │  Redis evidence cache (short-lived, TTL-bound)           │   │
│  │  PostgreSQL tool invocation ledger (metadata only)       │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  PiiRedactionPort → RegexPiiRedactionAdapter                     │
│  (EMAIL, PHONE, CREDIT_CARD, SSN, IP_ADDRESS, AADHAAR)          │
│                                                                  │
│  Redis: active stream + cancellation state                       │
│  PostgreSQL: durable conversation + request state                │
└──────────────┬──────────────────────────────────────────────────┘
               │ Kafka (inference.lifecycle.v1)
               ▼
┌─────────────────────────────────┐
│  ingestion-worker   :8082       │  Consumes Kafka → ClickHouse
└───────────┬─────────────────────┘
            │
            ▼
┌─────────────────────────────────┐      ┌──────────────────────┐
│  analytics-query    :8081       │◄─────│  ClickHouse :8123    │
│  - GET /v1/analytics/…          │      └──────────────────────┘
└─────────────────────────────────┘
```

### Request flow

1. Browser POSTs to `inference-gateway` with the user message.
2. Gateway runs `PiiRedactionPort` on all user messages before persistence. Detected PII is replaced with typed placeholders (`[EMAIL]`, `[PHONE]`, etc.); the redacted text is used for all downstream steps including the provider context.
3. Gateway creates or validates a conversation (PostgreSQL), saves the new redacted message, and opens an SSE stream to the browser.
4. For continuation requests, the gateway assembles provider context from exact messages for small conversations, or from a protected persisted context snapshot plus exact recent turns for long conversations.
5. The **Context Orchestrator** injects a platform runtime context system instruction (current date/time/timezone) into every provider request. For freshness-sensitive queries (market data, news, regulations, software versions, etc.) the `ToolNeedRouter` triggers the appropriate backend tools.
6. Tool adapters (`CoinGeckoMarketDataAdapter`, `TavilyWebSearchAdapter`) execute; results are normalized into evidence and cached in Redis. `tool.plan`, `tool.started`, `tool.completed`, and `source.available` SSE events are emitted before model tokens so the UI stays responsive.
7. Gateway calls the selected provider (Gemini or OpenAI) with the compacted and enriched context and pipes token chunks back as `token.delta` SSE events.
8. On completion, gateway persists the assistant message and publishes `inference.completed` to Kafka.
9. `ingestion-worker` consumes the Kafka event and writes a lifecycle fact to ClickHouse.
10. Operator dashboard queries `analytics-query` which reads from ClickHouse.

The web app proxies dashboard API calls through its same-origin `/analytics/api/*` route. In container and Kubernetes deployments, `ANALYTICS_API_INTERNAL_BASE` points that proxy at the internal `analytics-query` service so browsers do not need to resolve cluster-only service names.

### Logging strategy

- Raw prompts and completions are **never** logged, published to Kafka, or returned by analytics APIs.
- PII is redacted before persistence; the provider never receives unredacted user content after Phase 9 is enabled.
- Only content hashes, token counts, latency, status, and redacted category names are logged and published.
- Tool invocation rows store only metadata (hashes, source URLs, timing) — no raw query bodies or raw tool results.
- Structured JSON logs with `requestId`, `conversationId`, `tenantId`, `projectId` as correlation fields.
- OpenTelemetry traces exported to the OTel Collector (port 4318).

### SSE event types

| Event | Description |
|---|---|
| `request.accepted` | Conversation and request IDs confirmed |
| `token.delta` | Model token chunk |
| `message.delta` | Full incremental message text |
| `usage.delta` | Token usage update |
| `request.completed` | Stream finished successfully |
| `request.cancelled` | Cancellation acknowledged |
| `request.failed` | Error with structured error code |
| `tool.plan` | Tool execution plan (freshness-sensitive queries) |
| `tool.started` | Individual tool invocation began |
| `tool.completed` | Tool returned evidence |
| `tool.failed` | Tool error (inference continues with stale-answer warning) |
| `source.available` | Source provenance (URL, name, fetched timestamp) |
| `heartbeat` | Keep-alive during provider silence |

### Ingestion flow

```
SSE stream ends
  → InferenceGatewayService persists usage + assistant message (PostgreSQL)
  → publishes inference.completed to Kafka topic inference.lifecycle.v1
     → ingestion-worker consumes event
        → validates and maps to ClickHouse fact schema
        → inserts into llm_observability.inference_lifecycle_fact
```

The ingestion pipeline is event-based and decoupled. Gateway and analytics-query never communicate directly; ClickHouse is the analytics read model.

---

## Schema Design Decisions

### PostgreSQL (OLTP — source of truth)

| Table | Purpose |
|---|---|
| `conversation` | Conversation aggregate: tenant, project, status, title, timestamps |
| `conversation_message` | Persisted messages with role, **redacted** content, content hash, redaction state, sequence |
| `conversation_context_snapshot` | Protected derived summaries for provider context compaction in long conversations |
| `inference_request` | Request lifecycle: status, provider, model, idempotency key, timestamps |
| `inference_usage` | Token counts and estimated cost per request |
| `inference_error` | Error codes, failure stage, retryability per request |
| `inference_cancellation` | Cancellation reason, requestedBy, provider cancellation result |
| `model_catalog` | Enabled models per provider (Gemini, OpenAI) |
| `context_tool_invocation` | Tool execution metadata: tool name, status, latency, cache hit, source URLs, error code |

**Key decisions:**
- Conversation is the aggregate root. All messages belong to a conversation.
- `conversation_message` stores content only in PostgreSQL — not in Kafka or ClickHouse.
- `conversation_context_snapshot` stores derived protected summary content only in PostgreSQL — not in Kafka or ClickHouse.
- Context compaction checks run before every provider-bound request; small conversations are passed through exactly, while long conversations use an LLM-backed rolling summary plus exact recent turns. If compaction fails, the provider receives only the configured recent exact message window.
- After Phase 9, persisted content is the post-redaction text. `input_content_hash` on `inference_request` is computed from pre-redaction content to preserve idempotency semantics.
- `conversation_message.redaction_state` is `NONE` or `REDACTED`. When `REDACTED`, `metadata` carries `redactedCategories` (category names only, no raw values).
- `context_tool_invocation` stores only metadata — no raw prompts, raw result bodies, credentials, or provider secrets.
- Flyway manages all migrations. Two services (inference-gateway, ingestion-worker) use separate migration history tables to avoid conflicts.

### ClickHouse (Analytics — append-only facts)

| Table | Purpose |
|---|---|
| `inference_lifecycle_fact` | One row per lifecycle event (accepted, streaming, completed, failed, cancelled) |

**Key decisions:**
- Denormalised fact table: each row carries tenant, project, provider, model, status, tokens, latency, error code.
- ClickHouse's columnar storage makes aggregations (p95 latency, token totals, error rates) fast without indexes.
- Raw content is never written to ClickHouse.

### Redis (short-lived coordination)

- Active stream state: tracks whether a stream is running and whether cancellation was requested.
- 15-minute TTL after stream completion.
- Context evidence cache: short-lived tool result cache keyed by `context:tool-cache:<tenantId>:<toolName>:<inputHash>`.
- Not a source of truth — PostgreSQL is authoritative.

---

## PII Redaction

Phase 9 adds regex-based scanning of every user message before persistence. Redaction is enabled by default and configurable per deployment.

**Detected categories (initial set):**

| Category | Placeholder |
|---|---|
| `EMAIL` | `[EMAIL]` |
| `PHONE` | `[PHONE]` |
| `CREDIT_CARD` | `[CREDIT_CARD]` |
| `SSN` | `[SSN]` |
| `IP_ADDRESS` | `[IP_ADDRESS]` |
| `AADHAAR` | `[AADHAAR]` |

**Configuration:**

```yaml
llm-observability:
  redaction:
    enabled: true
    categories:
      - EMAIL
      - PHONE
      - CREDIT_CARD
      - SSN
      - IP_ADDRESS
      - AADHAAR
```

Or via environment variables: `PII_REDACTION_ENABLED=true`, `PII_REDACTION_CATEGORIES=EMAIL,PHONE,...`

**Privacy guarantees:**
- The provider never receives raw PII — redacted text is used for context assembly and conversation continuation.
- Redaction failure does not block inference; the stream continues with `RedactionState.NONE` and an error log.
- No raw matched values appear in logs, Kafka events, metrics, or traces.

---

## Context Orchestration and Live Data Grounding

Phase 8 adds a backend-owned Context Orchestrator inside the inference gateway.

**Runtime context injection:** Every provider request includes a platform-generated system instruction with the current date, time, and timezone. This is generated server-side at request time.

**Tool routing:** The `ToolNeedRouter` classifies queries into freshness-sensitive categories using deterministic rules. Tool-required categories include market data, recent news, regulations, public officials, software releases, weather, schedules, and high-stakes medical/legal/financial/security guidance.

**Available tools:**
- `MarketDataPort` → `CoinGeckoMarketDataAdapter` — crypto prices, market cap, 24h change
- `WebSearchPort` → `TavilyWebSearchAdapter` — source-backed web search with citations

**Evidence caching:** Redis caches tool results with category-appropriate TTLs (e.g. 15–60 s for market prices, 5–15 min for news). Cache misses invoke the upstream API; hits skip the network call entirely.

**Stale-answer fallback:** If required tools are unavailable or exceed timeout, the orchestrator adds an explicit stale-answer warning to provider context rather than letting the model invent current facts.

**Source provenance:** `source.available` SSE events carry source name, URL, and fetch timestamp for every evidence item. The UI renders these as expandable source chips.

---

## Providers

The platform uses a `ProviderClient` port and a `ProviderClientRegistry` so domain code is provider-agnostic.

| Provider | Adapter | Supported models |
|---|---|---|
| `gemini` | `GeminiProviderClient` | As seeded in `model_catalog` |
| `openai` | `OpenAiProviderClient` | `gpt-5.5`, `gpt-5.4` |

Live provider validation has been completed with Gemini. OpenAI adapter implementation exists, but live OpenAI provider testing is still pending.

OpenAI uses the Responses API (`POST /v1/responses`, `stream: true`). OpenAI provider-native cancellation is deferred; cancellation is best-effort through stream disposal.

The model catalog is queryable via `GET /v1/models`.

---

## Tradeoffs

| Decision | Tradeoff |
|---|---|
| **SSE over WebSocket** | Simpler server (WebFlux native); only server→client streaming; client uses `fetch` + `ReadableStream` to handle POST bodies |
| **PostgreSQL + ClickHouse split** | PostgreSQL gives ACID guarantees for conversation state; ClickHouse gives fast analytics scans. Adds operational complexity (two databases). |
| **Kafka for ingestion** | Decouples gateway from analytics write path; enables replay and multiple consumers. Adds Kafka dependency for a demo context. |
| **Backend-owned conversationId** | Prevents client from choosing IDs, avoids ID conflicts, simplifies idempotency. Client cannot predict conversationId before the first SSE event. |
| **No token-level SSE replay** | Reduces write volume and privacy risk. Reconnect after disconnect shows history at message granularity, not token granularity. |
| **Tenant/project as request params (no auth)** | Allows demo without an identity provider. Must be replaced with JWT-derived values before production use. |
| **Gemini and OpenAI providers** | Architecture is provider-agnostic (`ProviderClient` port + `ProviderClientRegistry`). Each provider needs catalog metadata, configuration, and an outbound adapter. |
| **Platform-mediated tools (not provider-native)** | Keeps tool policy, caching, rate limits, and audit metadata provider-neutral at the cost of more application code. |
| **Regex PII redaction (not NER)** | Fast and infrastructure-free; false positives/negatives possible. NER model adapter deferred as a second implementation behind the same port. |
| **Deterministic tool routing (not model-assisted)** | Easier to test and audit; less flexible than LLM-assisted planning. |
| **Metadata-only tool ledger** | Reduces privacy risk; raw evidence reconstruction depends on upstream source availability. |

---

## Future Extensions

1. **NER/ML-based PII detection** — Add a second `PiiRedactionPort` implementation backed by an NER model for higher recall on free-form text (full names, addresses, custom entity types). The port interface already supports multiple implementations; no domain change is needed.
2. **Per-tenant PII policy** — Move redaction configuration from global deployment settings to a per-tenant/project policy table. Enables selective category enablement or complete bypass per tenant.
3. **Provider-native tools** — Surface Gemini function calling or OpenAI tool use through a provider-aware tool adapter. Requires tracking provider response IDs across lifecycle state and an ADR update for tool ownership.
4. **Grafana dashboards** — Provision latency/throughput/error JSON dashboard files and PII/tool invocation panels so they load automatically on `make dev`.
5. **Authentication** — Replace hardcoded tenant/project with OIDC/JWT bearer tokens. Phase 6 spec and ADR-0016 are already written; `SecurityConfig` and `TenantProjectAuthorizer` stubs exist in the gateway.
6. **Token-level replay** — Add `inference_stream_event` table for reconnect at chunk granularity. Currently deferred; resume works at message granularity only.
7. **Live container integration tests** — Validate Flyway migrations, Kafka publish/consume, Redis state, and ClickHouse ingestion against real containers in CI.
8. **Conversation search** — Full-text or keyword search over conversation titles and message content (using redacted text only).
9. **Markdown rendering** — Render code blocks, bold, and lists in assistant message bubbles.
10. **Client-side error observability** — Emit OpenTelemetry JS traces for SSE connection errors and tool failure events.
11. **Assistant completion redaction** — Extend `PiiRedactionPort` to scan model completions. Deferred due to higher latency implications and differing semantic requirements.
12. **Retroactive redaction jobs** — Scheduled jobs to scan and redact PII from already-persisted messages. Requires an erasure policy and a separate Flyway migration.
13. **GDPR right-to-erasure** — Purge or anonymise conversation records on data-subject request. Requires a legal hold check before deletion.
14. **Additional grounding tools** — `WeatherPort`, `SportsDataPort`, `CompanyLookupPort`, `SoftwareVersionPort`, `SecurityAdvisoryPort` behind the existing `ToolRegistry` pattern.

---

## Scaling Considerations

- **inference-gateway** is stateless except for Redis-coordinated stream state. Horizontal scaling requires all instances to share the same Redis.
- **ingestion-worker** is a Kafka consumer group. Scale by adding replicas; Kafka partitions the load.
- **analytics-query** is read-only against ClickHouse. Scale horizontally; add ClickHouse projections or materialized views as cardinality grows.
- **ClickHouse** benefits from replica shards for high-throughput ingestion. The current schema works without them at demo scale.

## Failure Handling

- **Kafka publish failure**: Gateway logs the error and continues. Lifecycle facts may be missing but conversation state in PostgreSQL is unaffected.
- **ClickHouse write failure**: ingestion-worker retries via Kafka consumer offset. Events are not acknowledged until written.
- **Redis unavailable**: Stream cancellation may not propagate immediately. Evidence cache misses fall back to live API calls. PostgreSQL state remains authoritative; streams complete normally.
- **Provider timeout**: Gateway emits `request.failed` to the SSE stream and persists the error record.
- **Tool failure**: Context Orchestrator emits `tool.failed` SSE event and continues with a stale-answer warning injected into provider context. Inference is never blocked by tool errors.
- **PII redaction failure**: Gateway logs the error, leaves content unchanged with `RedactionState.NONE`, and continues inference without blocking the stream.

---

## Service Ports

| Service | Port |
|---|---|
| Web (chatbot + analytics) | 3000 |
| inference-gateway | 8080 |
| analytics-query | 8081 |
| ingestion-worker | 8082 |
| PostgreSQL | 5432 |
| Redis | 6379 |
| Kafka (host access) | 9094 |
| ClickHouse HTTP | 8123 |
| Prometheus | 9090 |
| Grafana | 3001 |
| OTel Collector gRPC | 4317 |
| OTel Collector HTTP | 4318 |

### Web API routing

- Analytics dashboard browser calls default to `/analytics/api`.
- The Next.js server forwards `/analytics/api/*` to `ANALYTICS_API_INTERNAL_BASE`, defaulting to `http://localhost:8081` for local development.
- `NEXT_PUBLIC_ANALYTICS_API_BASE` can still override the browser-visible base when an environment provides its own public analytics route.

---

## Specification and Decision Records

### Phase Specs

| Phase | Spec |
|---|---|
| Phase 00 | [Specification Foundation](docs/specs/phase-00-foundation.md) |
| Phase 01 | [Platform Bootstrap](docs/specs/phase-01-platform-bootstrap.md) |
| Phase 02 | [Inference Gateway MVP](docs/specs/phase-02-inference-gateway-mvp.md) |
| Phase 03 | [Inference Logging Pipeline](docs/specs/phase-03-inference-logging-pipeline.md) |
| Phase 04 | [Conversation Continuity](docs/specs/phase-04-conversation-continuity.md) |
| Phase 05 | [Analytics and Operator UI](docs/specs/phase-05-analytics-and-operator-ui.md) |
| Phase 06 | [Production Hardening](docs/specs/phase-06-production-hardening.md) |
| Phase 07 | [Chatbot UI](docs/specs/phase-07-chatbot-ui.md) |
| Phase 08 | [Context Orchestrator and Live Data Grounding](docs/specs/phase-08-context-orchestrator.md) |
| Phase 09 | [PII Redaction](docs/specs/phase-09-pii-redaction.md) |

### Architecture Decision Records

| ADR | Decision |
|---|---|
| [ADR-0001](docs/adr/ADR-0001-spec-driven-monorepo-governance.md) | Spec-Driven Monorepo Governance |
| [ADR-0002](docs/adr/ADR-0002-clean-architecture-domain-boundaries.md) | Clean Architecture and Domain Boundaries |
| [ADR-0003](docs/adr/ADR-0003-reactive-webflux-sse-streaming.md) | Reactive WebFlux and SSE Streaming |
| [ADR-0004](docs/adr/ADR-0004-kafka-domain-event-bus.md) | Kafka as Domain Event Bus |
| [ADR-0005](docs/adr/ADR-0005-postgresql-clickhouse-redis-persistence.md) | PostgreSQL, ClickHouse, and Redis Persistence Split |
| [ADR-0006](docs/adr/ADR-0006-opentelemetry-prometheus-grafana.md) | OpenTelemetry, Prometheus, and Grafana Observability |
| [ADR-0007](docs/adr/ADR-0007-docker-compose-kubernetes-helm.md) | Docker Compose, Kubernetes, and Helm Deployment |
| [ADR-0008](docs/adr/ADR-0008-multi-provider-llm-adapter-model.md) | Multi-Provider LLM Adapter Model |
| [ADR-0009](docs/adr/ADR-0009-maven-npm-workspace-bootstrap.md) | Maven and npm Workspace Bootstrap |
| [ADR-0010](docs/adr/ADR-0010-r2dbc-repositories-with-flyway-migrations.md) | R2DBC Repositories with Flyway Migrations |
| [ADR-0011](docs/adr/ADR-0011-inference-gateway-request-lifecycle-ownership.md) | Inference Gateway Request Lifecycle Ownership |
| [ADR-0012](docs/adr/ADR-0012-phase-02-gemini-conversation-content-and-redis.md) | Phase 2 Gemini, Conversation Content, and Redis Decisions |
| [ADR-0013](docs/adr/ADR-0013-phase-03-ingestion-pipeline-ownership.md) | Phase 3 Ingestion Pipeline Ownership |
| [ADR-0014](docs/adr/ADR-0014-phase-04-conversation-continuity.md) | Phase 4 Conversation Continuity |
| [ADR-0015](docs/adr/ADR-0015-phase-05-analytics-query-and-operator-ui.md) | Phase 5 Analytics Query and Operator UI |
| [ADR-0016](docs/adr/ADR-0016-phase-06-production-hardening-boundary.md) | Phase 6 Production Hardening Boundary |
| [ADR-0017](docs/adr/ADR-0017-chatbot-ui-routing-and-sse-design.md) | Chatbot UI Routing and SSE Design |
| [ADR-0018](docs/adr/ADR-0018-context-orchestrator-and-live-data-grounding.md) | Context Orchestrator and Live Data Grounding |
| [ADR-0019](docs/adr/ADR-0019-phase-09-pii-redaction.md) | Phase 9 PII Redaction |
| [ADR-0020](docs/adr/ADR-0020-openai-provider-adapter-and-model-catalog.md) | OpenAI Provider Adapter and Model Catalog |

### Other Architecture Docs

- [API Contracts](docs/architecture/api-contracts.md)
- [Event Schemas](docs/architecture/event-schemas.md)
- [Sequence Flows](docs/architecture/sequence-flows.md)
- [Local Development Strategy](docs/local-development-strategy.md)
- [Deployment Strategy](docs/deployment-strategy.md)
- [Observability Strategy](docs/observability-strategy.md)
- [Engineering Standards](docs/engineering-standards.md)
- [Repository Structure](docs/repository-structure.md)
- [Roadmap](docs/roadmap.md)
