# Observability Strategy

## Goals

- Make platform health, inference behavior, provider performance, and data pipeline correctness visible from the first implementation phase.
- Correlate browser actions, API requests, provider calls, Kafka events, persistence operations, and dashboard queries.
- Provide actionable SLOs and alerts for production operations.

## Scope

- OpenTelemetry tracing and metrics instrumentation.
- Prometheus metrics collection.
- Grafana dashboards.
- Structured logging.
- Health, readiness, and liveness endpoints.
- Kafka, PostgreSQL, ClickHouse, and Redis operational metrics.
- Inference-specific analytics in ClickHouse.

## Non-Goals

- Observability data is not a replacement for durable inference audit records.
- Prometheus is not the long-term store for token-level analytics.
- Raw prompts and completions should not be exposed in traces, metrics, or logs.

## Signal Strategy

| Signal | Tooling | Purpose |
| --- | --- | --- |
| Traces | OpenTelemetry | Request correlation and latency attribution |
| Metrics | Prometheus | Service health, SLOs, rates, saturation |
| Logs | Structured JSON logs | Debugging and audit-friendly operational records |
| Analytics | ClickHouse | High-volume inference telemetry and dashboard queries |
| Health | Spring Actuator and service probes | Runtime readiness and orchestration |

## Trace Model

Critical spans:

- Browser action.
- API request handling.
- Request validation.
- Provider adapter invocation.
- SSE stream lifecycle.
- Kafka publish.
- Kafka consume.
- PostgreSQL write.
- ClickHouse write.
- Redis cancellation/resume state operation.

Required correlation fields:

- `trace_id`
- `correlation_id`
- `tenant.id`
- `project.id`
- `conversation.id`
- `inference.request_id`
- `llm.provider`
- `llm.model`

## Metrics

Core service metrics:

- Request count, latency, and error rate by route and status.
- SSE stream count, duration, reconnects, and cancellations.
- Provider latency, timeout count, retry count, and error count.
- Token throughput by provider and model with controlled cardinality.
- Kafka producer send latency and failure count.
- Kafka consumer lag, processing latency, retry count, and dead-letter count.
- PostgreSQL connection pool saturation and query latency.
- ClickHouse insert batch size, insert latency, and query latency.
- Redis operation latency and error rate.

## Logging

Logs must be structured and include:

- Timestamp.
- Service name and version.
- Environment.
- Trace id and correlation id.
- Tenant and project identifiers where authorized and safe.
- Request id.
- Error code and class.
- Outcome.

Logs must not include:

- Provider API keys.
- Raw prompts or completions by default.
- Authorization headers.
- Unredacted PII.
- High-volume token chunks unless explicitly enabled in a secure diagnostic mode.

## Initial SLO Candidates

These are proposed starting points and must be validated with product and load-test data:

| Journey | Candidate SLO |
| --- | --- |
| Inference API availability | 99.9% successful non-provider-error requests |
| SSE first-token latency | p95 under provider-adjusted target |
| Cancellation acknowledgement | p95 under 500 ms once gateway receives cancellation |
| Event ingestion lag | p95 under 30 seconds from Kafka publish to ClickHouse visibility |
| Dashboard query latency | p95 under 2 seconds for default time windows |

## Dashboards

Baseline dashboards:

- Service overview: availability, latency, error rate, saturation.
- Inference overview: request rate, provider/model mix, token throughput, latency, failures.
- Streaming overview: active streams, duration, reconnects, cancellations.
- Pipeline overview: Kafka lag, consumer errors, PostgreSQL writes, ClickHouse ingestion.
- Provider reliability: timeout, retry, circuit breaker, and cost indicators.
- SLO overview: burn rates and alert status.

## Alerting

Alerting should prioritize symptoms over raw causes:

- API availability burn rate.
- SSE stream failure or cancellation anomaly.
- Kafka consumer lag sustained above threshold.
- ClickHouse ingestion failures.
- PostgreSQL connection pool exhaustion.
- Provider timeout spike.
- OTel collector or metrics scrape failure.

## Acceptance Criteria

- Every service exposes metrics and health endpoints.
- Every critical path emits correlated traces.
- Logs are structured and redacted.
- Dashboards cover service health, inference behavior, and ingestion health.
- Alerts and runbooks exist for production SLOs.

## Risks

- High-cardinality metrics can make Prometheus expensive or unstable.
- Over-instrumentation can add latency to streaming paths.
- Raw prompt/completion leakage is a privacy risk if logging controls are weak.

