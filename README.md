# LLM Observability Platform

A production-grade AI observability and inference logging platform with a streaming chatbot UI, event-driven ingestion pipeline, multi-turn conversation continuity, and an operator analytics dashboard.

## Quick Start

```bash
# 1. Install Node dependencies
npm install

# 2. Set your Gemini API key
export GEMINI_API_KEY=your_key_here

# 3. Start everything with one command
make dev
```

Open **http://localhost:3000** — the chatbot is ready. Open **http://localhost:3000/analytics** for the operator dashboard.

> Kafka, PostgreSQL, Redis, ClickHouse, Prometheus, Grafana, and all services start via Docker Compose.
> Compose also initializes the ClickHouse analytics schema used by `/analytics` and wires ClickHouse credentials into analytics services.

### Prerequisites

- Docker and Docker Compose
- Node.js ≥ 18
- Java 21 + Maven (only needed to run backend tests locally)

### Useful commands

| Command | Description |
|---|---|
| `make dev` | Start full stack (Docker Compose) |
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
┌─────────────────────────────────┐
│  inference-gateway  :8080       │  Spring Boot / WebFlux
│  - POST /v1/inference/stream    │  - Owns conversation lifecycle
│  - POST /v1/conversations/…     │  - Streams via SSE
│  - DELETE /v1/conversations/…   │  - Redis: active stream + cancel
│  - GET  /v1/conversations/…     │  - PostgreSQL: durable state
└───────────┬─────────────────────┘
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
2. Gateway creates a conversation (PostgreSQL), saves the message, and opens an SSE stream to the browser.
3. Gateway calls Gemini and pipes token chunks back as `token.delta` SSE events.
4. On completion, gateway persists the assistant message and publishes `inference.completed` to Kafka.
5. `ingestion-worker` consumes the Kafka event and writes a lifecycle fact to ClickHouse.
6. Operator dashboard queries `analytics-query` which reads from ClickHouse.

The web app proxies dashboard API calls through its same-origin `/analytics/api/*` route. In
container and Kubernetes deployments, `ANALYTICS_API_INTERNAL_BASE` points that proxy at the
internal `analytics-query` service so browsers do not need to resolve cluster-only service names.

### Logging strategy

- Raw prompts and completions are **never** logged, published to Kafka, or returned by analytics APIs.
- Only content hashes, token counts, latency, and status are logged and published.
- Structured JSON logs with `requestId`, `conversationId`, `tenantId`, `projectId` as correlation fields.
- OpenTelemetry traces exported to the OTel Collector (port 4318).

### Ingestion flow

```
SSE stream ends
  → InferenceGatewayService persists usage + assistant message (PostgreSQL)
  → publishes inference.completed to Kafka topic inference.lifecycle.v1
     → ingestion-worker consumes event
        → validates and maps to ClickHouse fact schema
        → inserts into llm_observability.inference_lifecycle_fact
```

The ingestion pipeline is event-based and decoupled. Gateway and analytics-query never
communicate directly; ClickHouse is the analytics read model.

---

## Schema Design Decisions

### PostgreSQL (OLTP — source of truth)

| Table | Purpose |
|---|---|
| `conversation` | Conversation aggregate: tenant, project, status, title, timestamps |
| `conversation_message` | Persisted messages with role, content hash, redaction state, sequence |
| `inference_request` | Request lifecycle: status, provider, model, idempotency key, timestamps |
| `inference_usage` | Token counts and estimated cost per request |
| `inference_error` | Error codes, failure stage, retryability per request |
| `inference_cancellation` | Cancellation reason, requestedBy, provider cancellation result |
| `model_catalog` | Enabled models per provider |

**Key decisions:**
- Conversation is the aggregate root. All messages belong to a conversation.
- `conversation_message` stores raw content only in PostgreSQL, not in Kafka or ClickHouse.
- Content hashes (SHA-256) allow integrity verification without re-reading content.
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
- Not a source of truth — PostgreSQL is authoritative.

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
| **One Gemini provider** | Architecture is provider-agnostic (`ProviderClient` port + `ProviderClientRegistry`). Adding OpenAI requires one new adapter class. |

---

## What I Would Improve With More Time

1. **PII redaction** — `RedactionState` is modelled; add regex/NER scanning on content before persistence.
2. **Second provider** — Add an `OpenAiProviderClient` to demonstrate the multi-provider adapter model.
3. **Grafana dashboards** — Provision latency/throughput/error JSON dashboard files so they load automatically on `make dev`.
4. **Authentication** — Replace hardcoded tenant/project with OIDC/JWT bearer tokens (Phase 6 spec already written).
5. **Markdown rendering** — Render code blocks, bold, and lists in assistant message bubbles.
6. **Client-side error observability** — Emit OpenTelemetry JS traces for SSE connection errors.
7. **Conversation search** — Full-text or keyword search over conversation titles and message content.
8. **Token-level replay** — Add `inference_stream_event` table for reconnect at chunk granularity.
9. **Live container integration tests** — Validate Flyway migrations and ClickHouse queries against real containers.

---

## Scaling Considerations

- **inference-gateway** is stateless except for Redis-coordinated stream state. Horizontal scaling requires all instances to share the same Redis.
- **ingestion-worker** is a Kafka consumer group. Scale by adding replicas; Kafka partitions the load.
- **analytics-query** is read-only against ClickHouse. Scale horizontally; add ClickHouse projections or materialized views as cardinality grows.
- **ClickHouse** benefits from replica shards for high-throughput ingestion. The current schema works without them at demo scale.

## Failure Handling

- **Kafka publish failure**: Gateway logs the error and continues. Lifecycle facts may be missing but conversation state in PostgreSQL is unaffected.
- **ClickHouse write failure**: ingestion-worker retries via Kafka consumer offset. Events are not acknowledged until written.
- **Redis unavailable**: Stream cancellation may not propagate immediately. PostgreSQL state remains authoritative; streams complete normally.
- **Provider timeout**: Gateway emits `request.failed` to the SSE stream and persists the error record.

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

- [Phase 02 — Inference Gateway MVP](docs/specs/phase-02-inference-gateway-mvp.md)
- [Phase 03 — Inference Logging Pipeline](docs/specs/phase-03-inference-logging-pipeline.md)
- [Phase 04 — Conversation Continuity](docs/specs/phase-04-conversation-continuity.md)
- [Phase 05 — Analytics and Operator UI](docs/specs/phase-05-analytics-and-operator-ui.md)
- [Phase 07 — Chatbot UI](docs/specs/phase-07-chatbot-ui.md)
- [Phase 08 — Context Orchestrator and Live Data Grounding](docs/specs/phase-08-context-orchestrator.md)
- [Architecture Decision Records](docs/adr/README.md)
- [API Contracts](docs/architecture/api-contracts.md)
- [Event Schemas](docs/architecture/event-schemas.md)
