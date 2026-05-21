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
- Validation at request boundaries.
- Deterministic error envelope.
- Trace context propagation.
- Idempotency keys where retries can create duplicate work.
- Pagination and time-window limits for collection and analytics APIs.
- Rate limiting and request size limits once tenancy is defined.

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
| Cancel inference | DELETE | `/v1/inference/{requestId}/stream` | Request cancellation for an active stream |
| Inference status | GET | `/v1/inference/{requestId}` | Read request status and summary |
| Conversation create | POST | `/v1/conversations` | Create a conversation |
| Conversation get | GET | `/v1/conversations/{conversationId}` | Read conversation metadata and state |
| Conversation events | GET | `/v1/conversations/{conversationId}/events` | Replay or resume conversation events |
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
- `heartbeat`

SSE requirements:

- Each event includes a stable event id suitable for client-side deduplication.
- Each event includes request id, conversation id, and trace id.
- Clients may resume with an `after` cursor when supported.
- Cancellation is best-effort if provider APIs do not guarantee cancellation.

## Phase 2 Inference Gateway Contract Requirements

Phase 2 must expand `libs/contracts/openapi/inference-gateway.v1.yaml` before implementation.

Required endpoints:

| API | Method | Path | Required in Phase 2 |
| --- | --- | --- | --- |
| Inference stream | POST | `/v1/inference/stream` | Yes |
| Cancel inference | DELETE | `/v1/inference/{requestId}/stream` | Yes |
| Inference status | GET | `/v1/inference/{requestId}` | Yes |

Required streaming behavior:

- `POST /v1/inference/stream` returns `text/event-stream`.
- Request bodies must not include `conversationId`.
- The gateway creates a UUID conversation with a UI-ready title for every streaming inference request.
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
