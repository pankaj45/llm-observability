# Local Development Strategy

## Goals

- Make the full platform reproducible on a developer machine.
- Support fast inner-loop development for frontend, backend, contracts, and infrastructure.
- Keep local behavior close enough to production to catch integration issues early.

## Scope

- Docker Compose based local dependencies.
- Service-level Dockerfiles.
- Local OpenTelemetry collector, Prometheus, and Grafana.
- PostgreSQL, ClickHouse, Redis, and Kafka.
- Contract validation and integration test support.
- Seed data for dashboards and trace exploration.

## Non-Goals

- Local development will not emulate every Kubernetes control-plane behavior.
- Local secrets are for development only and must not resemble production secret management.
- Local environments are not a substitute for staging performance validation.

## Proposed Local Topology

```text
Developer
├── apps/web
├── services/inference-gateway
├── services/ingestion-worker
├── services/analytics-query
└── docker-compose dependencies
    ├── PostgreSQL
    ├── ClickHouse
    ├── Redis
    ├── Kafka
    ├── OpenTelemetry Collector
    ├── Prometheus
    └── Grafana
```

## Implemented Commands

Phase 1 provides these commands:

```text
npm install             # install frontend and contract tooling dependencies
make contracts          # validate OpenAPI and event schemas
make test               # run all test suites
make test-backend       # run Maven tests for Spring Boot services
make test-frontend      # run TypeScript check for the web app
make dev                # start local dependencies and app services
make down               # stop local dependencies
```

## Local Ports

| Component | Port | Notes |
| --- | ---: | --- |
| Web app | 3000 | Next.js development server |
| Inference gateway | 8080 | Public API and SSE |
| Analytics query | 8081 | Query APIs |
| Ingestion worker | 8082 | Health and metrics only |
| PostgreSQL | 5432 | Local database |
| ClickHouse HTTP | 8123 | Analytics database |
| Redis | 6379 | Cache and cancellation state |
| Kafka | 9092 | Local event bus |
| Prometheus | 9090 | Metrics |
| Grafana | 3001 | Dashboards |
| OTel collector | 4317/4318 | OTLP gRPC/HTTP |

## Data and Migrations

- PostgreSQL migrations should be versioned and repeatable in local and CI environments.
- ClickHouse migrations should be explicit and reviewed with expected query patterns.
- Phase 1 wires Flyway dependencies into backend services, but business migrations begin with domain implementation specs.
- Seed data should be deterministic and safe to reset.
- Local reset commands must never target production-like connection strings.

## Developer Observability

Local development must make these easy to inspect:

- API traces from browser to backend to provider adapter.
- Kafka event flow from producer to consumer.
- PostgreSQL and ClickHouse writes for a single inference request.
- Prometheus metrics for request latency, token throughput, provider errors, and consumer lag.
- Structured logs correlated by trace id and request id.

## Acceptance Criteria

- A new developer can bootstrap dependencies and run all tests from documented commands.
- Local Compose starts all required dependencies with health checks.
- Service logs include trace and correlation identifiers.
- Local Grafana dashboards show baseline health and inference metrics once services exist.
- README and architecture docs remain in sync with actual commands and ports.

## Risks

- Local Kafka and ClickHouse can be resource intensive.
- Divergence between Docker Compose and Kubernetes can hide production-only issues.
- Provider credentials must be handled carefully to avoid accidental leakage through logs or shell history.
