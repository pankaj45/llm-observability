# ADR-0006: OpenTelemetry, Prometheus, and Grafana Observability

## Context

The platform itself is an observability system and must be observable from the first implementation phase. It must correlate frontend actions, backend requests, provider calls, Kafka events, persistence, and analytics queries.

## Decision

Use OpenTelemetry for traces and instrumentation, Prometheus for metrics collection, and Grafana for dashboards and alert visualization. Services must emit structured logs with trace and correlation identifiers.

## Alternatives Considered

- Vendor-specific APM as the primary instrumentation layer.
- Logs-only observability.
- Metrics-only observability.
- Custom telemetry pipeline.

## Tradeoffs

- OpenTelemetry keeps instrumentation portable but requires consistent semantic conventions.
- Prometheus is strong for service metrics and SLOs but unsuitable for raw token-level analytics.
- Grafana provides flexible dashboards but requires disciplined dashboard ownership.
- Structured logs increase debugging value but require redaction controls.

## Consequences

- Every service must expose metrics and health endpoints.
- Critical paths must emit traces with controlled-cardinality attributes.
- Raw prompts, completions, credentials, and sensitive metadata must not be logged by default.
- Dashboards and alerts become part of production readiness acceptance criteria.

