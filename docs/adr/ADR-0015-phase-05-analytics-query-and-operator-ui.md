# ADR-0015: Phase 5 Analytics Query and Operator UI

## Context

Phase 5 introduces operator-facing analytics and a web dashboard after the platform has a streaming inference gateway, durable lifecycle event ingestion, ClickHouse lifecycle facts, and conversation continuity APIs. The main architectural question is whether analytics should query PostgreSQL canonical lifecycle records, ClickHouse lifecycle facts, a new materialized read model, or a BI-style arbitrary query surface.

The platform already split authoritative OLTP state from analytics state. PostgreSQL owns protected conversation content and request lifecycle correctness. ClickHouse owns append-oriented lifecycle facts derived from Kafka events. Raw prompt and completion content must not be published to Kafka or returned by analytics APIs.

## Decision

Phase 5 analytics query APIs will use ClickHouse `inference_lifecycle_fact` as the primary read store. PostgreSQL remains authoritative for protected content and canonical request state, but the Phase 5 analytics endpoints will not return raw prompt, completion, or token text.

The analytics query service will expose three contract-first endpoints:

- `GET /v1/analytics/inference/summary`
- `GET /v1/analytics/inference/requests`
- `GET /v1/analytics/inference/requests/{requestId}`

All endpoints require tenant id, project id, and an explicit time window. Query windows are capped at 30 days. Request search uses opaque server-owned cursors, defaults to 50 items, and is capped at 200 items.

The Next.js operator UI will consume these APIs through a same-origin web proxy and provide dashboard KPIs, time-series trends, provider/model/status/error breakdowns, request search, and request detail. The proxy only handles browser routing to the analytics-query service; `services/analytics-query` remains the contract owner for `/v1/analytics/*`. Authentication remains deferred to Phase 6; Phase 5 preserves explicit tenant/project request parameters.

Cost is exposed as an API placeholder through `estimatedCostUsd`, but remains zero or null until provider pricing catalog and cost computation are implemented.

## Alternatives Considered

- Query PostgreSQL for dashboard analytics.
- Add a new PostgreSQL analytics read model.
- Add ClickHouse materialized views or projections before the first dashboard.
- Expose an arbitrary SQL workbench to operators.
- Add new Kafka conversation or token-level analytics events.
- Wait for authentication before building any operator UI.

## Tradeoffs

- ClickHouse is better suited for analytics scans and aggregations, but adds SQL mapping and live database validation work.
- Querying lifecycle facts keeps raw content out of analytics, but request-level views must aggregate event-level rows carefully.
- Avoiding materialized views keeps Phase 5 smaller, but high-cardinality tenants may need projections later.
- Opaque cursors preserve server flexibility, but clients cannot reason about sort keys.
- Building the dashboard before auth provides useful product feedback, but tenant/project parameters remain a temporary trust boundary.
- Cost placeholders make the API shape stable, but cost numbers are incomplete until pricing is implemented.

## Consequences

- `services/analytics-query` owns ClickHouse query composition and response mapping.
- `apps/web` owns the same-origin dashboard proxy so browser clients do not depend on internal service DNS.
- Analytics APIs must validate tenant/project scope, explicit time windows, maximum window length, filters, and pagination limits.
- Analytics responses must not include raw prompts, completions, authorization headers, credentials, or provider secrets.
- Dashboard code must handle loading, empty, error, and degraded states.
- Query performance must be revisited with seeded and live ClickHouse data before production hardening.
- Future cost attribution, materialized views, projections, or auth changes require ADR updates if they change the query model or user trust boundary.
