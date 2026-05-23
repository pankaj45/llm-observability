# Phase 05 Specification: Analytics and Operator UI

## Implementation Status

Status as of 2026-05-23: approved for implementation. Phase 5 builds on the Phase 3 ClickHouse lifecycle fact table and the Phase 4 conversation APIs. Docker image validation, live ClickHouse/Kafka/PostgreSQL integration testing, and provider-live analytics validation remain deferred until explicitly requested.

## Goals

- Provide operator-facing visibility into inference volume, latency, token usage, cost placeholders, error rates, providers, models, and request traces.
- Implement contract-first analytics query APIs backed by ClickHouse lifecycle facts.
- Deliver a usable Next.js and Mantine operator dashboard that reads real analytics query APIs.
- Keep raw prompt and completion content out of analytics APIs and dashboard responses.
- Preserve clean architecture boundaries in the analytics query service.

## Scope

- `services/analytics-query` query API implementation.
- `libs/contracts/openapi/analytics-query.v1.yaml` expansion.
- ClickHouse-backed read model over `llm_observability.inference_lifecycle_fact`.
- Summary endpoint for dashboard KPIs, time series, provider/model breakdowns, status breakdowns, and top errors.
- Request search endpoint with filters, pagination, and stable opaque cursors.
- Request detail endpoint for one request trace without raw content.
- Next.js operator dashboard in `apps/web`.
- Same-origin dashboard proxy in `apps/web` for forwarding browser analytics requests to `services/analytics-query`.
- Focused controller, service, and query mapping tests.
- Documentation updates for API contracts, sequence flows, README, local setup, and ADR index.

## Non-Goals

- No arbitrary SQL workbench.
- No self-service billing portal or chargeback workflow.
- No raw prompt/completion analytics, token text, or semantic content search.
- No authentication provider implementation; tenant/project scoping remains request-parameter based until Phase 6.
- No new Kafka event types.
- No token-level stream analytics or durable token replay.
- No live container-backed analytics performance validation unless explicitly requested.

## Assumptions

- ClickHouse `inference_lifecycle_fact` is the primary analytics source.
- PostgreSQL remains the source of truth for protected raw conversation content and canonical lifecycle state.
- Phase 5 analytics are eventually consistent with gateway activity because they depend on Kafka ingestion.
- Inference lifecycle events remain replay-safe and may create duplicate rows only if ingestion idempotency is bypassed.
- Cost attribution is exposed as a nullable or zero-value placeholder until provider pricing catalogs and cost computation are implemented.
- Operator dashboard users can supply tenant, project, time window, and optional provider/model/status filters.

## Resolved Decisions

1. Phase 5 uses ClickHouse as the primary analytics read store and does not query raw conversation messages.
2. The MVP implements:
   - `GET /v1/analytics/inference/summary`
   - `GET /v1/analytics/inference/requests`
   - `GET /v1/analytics/inference/requests/{requestId}`
3. Analytics endpoints require `tenantId`, `projectId`, `from`, and `to`.
4. Optional filters are `provider`, `model`, `status`, and `errorCode`.
5. Query windows are capped at 30 days.
6. Request search defaults to 50 items and is capped at 200 items.
7. Public cursors are opaque server-owned strings.
8. Dashboard p95 targets on deterministic seeded analytics data are 750 ms for summary and 1000 ms for request search/detail.
9. Analytics APIs do not return raw prompt or completion content.
10. Authentication and authorization remain Phase 6 work.

## API Contract

The OpenAPI contract in `libs/contracts/openapi/analytics-query.v1.yaml` is authoritative.

### `GET /v1/analytics/inference/summary`

Required query parameters:

- `tenantId`
- `projectId`
- `from`
- `to`

Optional query parameters:

- `provider`
- `model`
- `status`

Response fields:

- `tenantId`
- `projectId`
- `window`
- `totals`
- `timeSeries`
- `providers`
- `models`
- `statuses`
- `topErrors`
- `degraded`

### `GET /v1/analytics/inference/requests`

Required query parameters:

- `tenantId`
- `projectId`
- `from`
- `to`

Optional query parameters:

- `provider`
- `model`
- `status`
- `errorCode`
- `limit`
- `cursor`

Response fields:

- `items`
- `nextCursor`
- `degraded`

Rows include request id, conversation id, provider, model, latest status, start/end timestamps, duration, token usage, error code, failure stage, correlation id, and traceparent.

### `GET /v1/analytics/inference/requests/{requestId}`

Required query parameters:

- `tenantId`
- `projectId`
- `from`
- `to`

Response fields:

- request identifiers and correlation fields
- provider/model/status
- timing and usage summary
- lifecycle events
- error/cancellation fields
- `conversationId` for linking to conversation APIs

## Data Model Impact

Phase 5 does not require PostgreSQL schema changes.

ClickHouse reads from:

- `llm_observability.inference_lifecycle_fact`

Recommended query access pattern:

- filter by `(tenant_id, project_id, occurred_at)`
- aggregate by request id for request search/detail
- aggregate by provider/model/status/error for dashboard breakdowns
- group by hourly buckets for dashboard time series

Cost fields are represented in API responses as `estimatedCostUsd`, but remain zero until lifecycle facts contain computed provider cost.

## Observability Impact

- Analytics endpoints emit request count, latency, and error metrics through Spring WebFlux/Actuator.
- Query service traces include tenant, project, route, query window, and result count attributes with cardinality controls.
- ClickHouse failures return deterministic error envelopes.
- Dashboard handles loading, empty, error, and degraded states.
- The dashboard calls analytics through `/analytics/api/*`; the web server forwards to the internal analytics-query base configured by `ANALYTICS_API_INTERNAL_BASE`.

## Acceptance Criteria

- Analytics OpenAPI contract validates through `npm run contracts`.
- Summary endpoint returns totals, breakdowns, time series, and top errors for a scoped tenant/project window.
- Request search supports filters, pagination, and opaque cursors.
- Request detail returns lifecycle trace data without raw prompt/completion content.
- Query service has controller and application tests.
- Dashboard renders KPI, breakdown, time-series, request table, and detail states from the API.
- Frontend TypeScript checks pass.
- `mvn -pl services/analytics-query test` passes.
- README, architecture docs, local setup, and ADR index are updated.

## Risks

- Lifecycle facts are event-level, so request-level analytics must aggregate carefully to avoid double counting.
- Query latency can degrade as cardinality grows without projections or materialized views.
- Cost visibility remains incomplete until provider pricing and cost computation are implemented.
- Without live container-backed verification, ClickHouse SQL and auth behavior still need a later explicit validation pass.
