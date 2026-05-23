# API Contracts

## Contract Policy

All APIs must be specified in OpenAPI before implementation. Generated or contract-validated server and client code should be used where practical.

Planned contract location:

```text
libs/contracts/openapi/
├── inference-gateway.v1.yaml
├── conversation.v1.yaml
└── analytics-query.v1.yaml
```

## Common API Requirements

- HTTPS only outside local development.
- Tenant and project scoping on all business APIs.
- Bearer token authentication on all business APIs once Phase 6 hardening is enabled.
- Validation at request boundaries.
- Deterministic error envelope.
- Trace context propagation.
- Idempotency keys where retries can create duplicate work.
- Pagination and time-window limits for collection and analytics APIs.
- Rate limiting and request size limits once tenancy is defined.

## Phase 6 Security Contract Requirements

Phase 6 adds OIDC/JWT authentication and authorization requirements to existing business APIs.

Required behavior:

- Health, readiness, liveness, and Prometheus endpoints remain controlled by deployment/network policy.
- Business APIs require `Authorization: Bearer <token>`.
- JWTs are validated for issuer, audience, expiration, signature, and required claims.
- Tenant/project request scope must match authorized JWT claims.
- Authorization failures use the deterministic error envelope.
- OpenAPI specs document bearer security schemes and endpoint security requirements.

Recommended scopes:

- `inference:write`
- `inference:read`
- `conversation:read`
- `conversation:write`
- `analytics:read`
- `admin:read`

## Error Envelope

```json
{
  "error": {
    "code": "string",
    "message": "string",
    "details": [],
    "traceId": "string"
  }
}
```

## Initial Endpoint Inventory

| API | Method | Path | Purpose |
| --- | --- | --- | --- |
| Inference stream | POST | `/v1/inference/stream` | Start a streaming inference request |
| Conversation message stream | POST | `/v1/conversations/{conversationId}/messages/stream` | Continue an existing conversation |
| Cancel inference | DELETE | `/v1/inference/{requestId}/stream` | Request cancellation for an active stream |
| Inference status | GET | `/v1/inference/{requestId}` | Read request status and summary |
| Conversation list | GET | `/v1/conversations` | List conversation metadata |
| Conversation get | GET | `/v1/conversations/{conversationId}` | Read conversation metadata |
| Conversation messages | GET | `/v1/conversations/{conversationId}/messages` | Read persisted conversation messages |
| Conversation events | GET | `/v1/conversations/{conversationId}/events` | Read timeline history or replay active stream events |
| Cancel conversation stream | DELETE | `/v1/conversations/{conversationId}/stream` | Cancel active stream work for a conversation |
| Analytics summary | GET | `/v1/analytics/inference/summary` | Dashboard summary metrics |
| Analytics requests | GET | `/v1/analytics/inference/requests` | Search inference requests |
| Analytics request detail | GET | `/v1/analytics/inference/requests/{requestId}` | Inspect one request trace |
| Provider list | GET | `/v1/providers` | List configured provider capabilities |

## Streaming Inference Contract Sketch

Request fields:

- `tenantId`
- `projectId`
- `provider`
- `model`
- `messages`
- `parameters`
- `metadata`
- `idempotencyKey`

SSE event types:

- `request.accepted`
- `token.delta`
- `message.delta`
- `usage.delta`
- `request.completed`
- `request.cancelled`
- `request.failed`
- `tool.plan`
- `tool.started`
- `tool.completed`
- `tool.failed`
- `source.available`
- `heartbeat`

SSE requirements:

- Each event includes a stable event id suitable for client-side deduplication.
- Each event includes request id, conversation id, and trace id.
- Clients may resume with an `after` cursor when supported.
- Cancellation is best-effort if provider APIs do not guarantee cancellation.
- Tool progress events are optional and may appear before model token events when live data grounding is enabled.

## Phase 2 Inference Gateway Contract Requirements

Phase 2 must expand `libs/contracts/openapi/inference-gateway.v1.yaml` before implementation.

Required endpoints:

| API | Method | Path | Required in Phase 2 |
| --- | --- | --- | --- |
| Inference stream | POST | `/v1/inference/stream` | Yes |
| Conversation message stream | POST | `/v1/conversations/{conversationId}/messages/stream` | Yes |
| Cancel inference | DELETE | `/v1/inference/{requestId}/stream` | Yes |
| Inference status | GET | `/v1/inference/{requestId}` | Yes |

Required streaming behavior:

- `POST /v1/inference/stream` returns `text/event-stream`.
- Request bodies must not include `conversationId`.
- The gateway creates a UUID conversation with a UI-ready title for every new streaming inference request.
- `POST /v1/conversations/{conversationId}/messages/stream` continues an existing conversation, appends only the new turn, and loads prior messages server-side for provider context.
- Each SSE event includes stable `id`, `event`, and JSON `data`.
- Event ids are monotonic within a request.
- Heartbeats are emitted during provider silence.
- Client disconnect attempts to stop provider streaming work.

Required cancellation behavior:

- Cancellation returns a deterministic JSON result when the request is known and cancellation is accepted.
- Cancellation is best-effort where provider APIs do not guarantee cancellation.
- Cancellation emits a durable lifecycle event.

Required status behavior:

- Status response includes lifecycle state, provider, model, timestamps, usage summary, cancellation summary, and error summary.
- Status response excludes raw prompt and completion content by default.
- Raw conversation content is available only through authorized conversation/message APIs with redaction policy.

## Phase 4 Conversation Contract Requirements

Phase 4 expands `libs/contracts/openapi/conversation.v1.yaml` for conversation continuity.

Required endpoints:

| API | Method | Path | Required in Phase 4 |
| --- | --- | --- | --- |
| Conversation list | GET | `/v1/conversations` | Yes |
| Conversation get | GET | `/v1/conversations/{conversationId}` | Yes |
| Conversation messages | GET | `/v1/conversations/{conversationId}/messages` | Yes |
| Conversation events | GET | `/v1/conversations/{conversationId}/events` | Yes |
| Cancel conversation stream | DELETE | `/v1/conversations/{conversationId}/stream` | Yes |

Required continuity behavior:

- Conversation list/get returns metadata only.
- Conversation messages returns raw content only after tenant/project scoping.
- Conversation events supports `mode=history` JSON pages and `mode=stream` active replay.
- Public history cursors are opaque server-owned strings.
- Pagination defaults to 50 items and is capped at 200 items.
- Durable timeline history is derived from canonical PostgreSQL records.
- Redis active stream replay is short-lived and best-effort.
- Concurrent active continuation streams for the same conversation are rejected.
- Phase 4 does not publish new `conversation.*` Kafka events.

## Phase 5 Analytics Query Contract Requirements

Phase 5 expands `libs/contracts/openapi/analytics-query.v1.yaml` for operator analytics.

Required endpoints:

| API | Method | Path | Required in Phase 5 |
| --- | --- | --- | --- |
| Analytics summary | GET | `/v1/analytics/inference/summary` | Yes |
| Analytics requests | GET | `/v1/analytics/inference/requests` | Yes |
| Analytics request detail | GET | `/v1/analytics/inference/requests/{requestId}` | Yes |

Required analytics behavior:

- All endpoints require `tenantId`, `projectId`, `from`, and `to`.
- Query windows are capped at 30 days.
- Optional filters include provider, model, status, and error code where applicable.
- Request search defaults to 50 items and is capped at 200 items.
- Public cursors are opaque server-owned strings.
- Analytics APIs read ClickHouse lifecycle facts and do not return raw prompt/completion content.
- Cost fields are placeholders until provider pricing and cost computation are implemented.

## Validation Requirements

- Provider and model must be supported for the tenant/project.
- Message roles and content blocks must match the provider-normalized schema.
- Token, timeout, and temperature parameters must be bounded.
- Metadata keys and values must have size limits.

## Observability Requirements

Every API must emit:

- Request count by route and status.
- Latency histogram by route.
- Error count by error code.
- Trace span with tenant/project/request attributes.
- Structured logs for accepted, completed, cancelled, and failed outcomes.

## Test Requirements

Every API must have:

- OpenAPI validation in CI.
- Contract tests for successful and failed requests.
- Validation tests.
- Error envelope tests.
- Authorization tests once auth is implemented.
- Observability assertions for critical path traces and metrics.
