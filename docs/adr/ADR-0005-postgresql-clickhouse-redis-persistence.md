# ADR-0005: PostgreSQL, ClickHouse, and Redis Persistence Split

## Context

The platform has multiple data access patterns: authoritative tenant/project/conversation/request state, high-volume analytical telemetry, and short-lived cancellation/resume/cache state. A single datastore would force poor tradeoffs.

## Decision

Use PostgreSQL for OLTP state, ClickHouse for analytics facts, and Redis for short-lived cache, cancellation, and resume cursor state.

## Alternatives Considered

- PostgreSQL only.
- ClickHouse only.
- Elasticsearch or OpenSearch as primary analytics store.
- Redis as event and state store.

## Tradeoffs

- Polyglot persistence increases operational and migration complexity.
- PostgreSQL provides transactional correctness for authoritative state.
- ClickHouse provides efficient columnar analytics for high-volume inference telemetry.
- Redis provides low-latency ephemeral coordination but must not become the only source of truth.

## Consequences

- Data lineage must be documented from API request to Kafka event to PostgreSQL and ClickHouse records.
- Migrations must be managed separately for PostgreSQL and ClickHouse.
- Analytics queries must account for eventual consistency from Kafka ingestion.
- Redis data must have TTLs and fallback paths to PostgreSQL where correctness matters.

