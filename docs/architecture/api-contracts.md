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
- `conversationId`
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

## Validation Requirements

- Provider and model must be supported for the tenant/project.
- Message roles and content blocks must match the provider-normalized schema.
- Token, timeout, and temperature parameters must be bounded.
- Metadata keys and values must have size limits.
- Conversation id must belong to the tenant/project.

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

