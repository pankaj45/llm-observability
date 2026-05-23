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
- Phase 2 adds the inference gateway Flyway migration for provider/model, conversation, message, request, usage, error, and cancellation tables.
- Phase 3 adds ingestion-worker Flyway migration state in a separate `ingestion_worker_flyway_schema_history` table to avoid checksum collisions with inference-gateway migrations sharing the same local PostgreSQL database.
- Docker Compose applies the Phase 3 ClickHouse schema from `infra/migrations/clickhouse/V1__phase_03_inference_lifecycle_fact.sql` through ClickHouse init scripts.
- Phase 5 analytics query APIs read the same ClickHouse lifecycle fact table and require deterministic seed data before dashboard performance validation.
- Seed data should be deterministic and safe to reset.
- Local reset commands must never target production-like connection strings.

## Phase 2 Inference Gateway

- Set `GEMINI_API_KEY` in the shell before starting Compose if live Gemini calls are required.
- Docker Compose wires the inference gateway to PostgreSQL, Redis, Kafka, and the OpenTelemetry collector.
- `POST /v1/inference/stream` rejects request bodies with `conversationId`; the gateway always creates the conversation id and title.
- Kafka lifecycle publishing is enabled in Compose through `INFERENCE_KAFKA_ENABLED=true`.
- Docker image verification is intentionally deferred to the later image-testing pass.

## Phase 3 Ingestion Worker

- Docker Compose enables Kafka lifecycle consumption with `INGESTION_KAFKA_ENABLED=true`.
- Docker Compose enables ClickHouse lifecycle fact writes with `INGESTION_CLICKHOUSE_ENABLED=true`.
- The worker consumes `inference.lifecycle.v1`, records dedupe/processing state in PostgreSQL, and writes analytics facts to ClickHouse.
- Container-backed Kafka/PostgreSQL/ClickHouse integration tests are deferred until explicitly requested.

## Phase 5 Analytics Query and Dashboard

- Docker Compose wires the analytics query service to ClickHouse through `CLICKHOUSE_HTTP_URL`.
- Docker Compose configures ClickHouse with `CLICKHOUSE_USERNAME` and `CLICKHOUSE_PASSWORD`, defaulting locally to `default` / `password`, and passes the same credentials to analytics-query and ingestion-worker.
- The operator dashboard reads `NEXT_PUBLIC_ANALYTICS_API_BASE`, defaulting to the same-origin `/analytics/api` proxy.
- The web proxy forwards `/analytics/api/*` to `ANALYTICS_API_INTERNAL_BASE`, defaulting to `http://localhost:8081`; Docker Compose points it at `http://analytics-query:8081`.
- Analytics APIs require `tenantId`, `projectId`, `from`, and `to`.
- Analytics responses exclude raw prompt and completion content.
- Live ClickHouse query validation and seeded load testing are deferred until explicitly requested.

## Developer Observability

Local development must make these easy to inspect:

- API traces from browser to backend to provider adapter.
- Kafka event flow from producer to consumer.
- PostgreSQL and ClickHouse writes for a single inference request.
- Prometheus metrics for request latency, token throughput, provider errors, and consumer lag.
- Structured logs correlated by trace id and request id.

## Phase 6 Local Security

- Local development uses a mock OIDC issuer or signed development tokens.
- Business API examples should include bearer tokens after Phase 6 security is enabled.
- Local tokens must use non-production issuer, audience, and signing keys.
- Local secret values must never resemble production credentials.

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
