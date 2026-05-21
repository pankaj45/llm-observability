# Repository Structure

## Intent

This repository is a spec-driven monorepo. Phase 1 created the first implemented slices of the target structure: backend service shells, frontend shell, contracts, local infrastructure, CI, and docs.

## Target Top-Level Structure

```text
.
├── apps/
│   └── web/                         # Next.js + Mantine operator UI
├── services/
│   ├── inference-gateway/           # WebFlux API for inference, SSE, cancellation
│   ├── ingestion-worker/            # Kafka consumers and persistence pipeline
│   └── analytics-query/             # Query APIs over ClickHouse/PostgreSQL
├── libs/
│   ├── contracts/
│   │   ├── openapi/                 # Versioned OpenAPI specs
│   │   └── events/                  # Versioned JSON/Avro event schemas
│   ├── java-common/                 # Future shared Java utilities with strict dependency rules
│   └── test-support/                # Future contract, container, and fixture utilities
├── infra/
│   ├── docker-compose/              # Local development topology
│   ├── helm/                        # Future Kubernetes Helm charts
│   ├── k8s/                         # Future optional raw manifests and examples
│   ├── observability/               # Prometheus, Grafana, OTel collector config
│   └── migrations/                  # Future PostgreSQL and ClickHouse migrations
├── docs/
│   ├── adr/                         # Architecture decision records
│   ├── architecture/                # Diagrams, contracts, package structures
│   └── specs/                       # Phase and feature specifications
└── README.md
```

## Backend Package Structure

Spring Boot services should follow clean architecture. Package names below are examples and must be adapted to each service boundary.

```text
com.company.observability.<service>
├── domain/
│   ├── model/                       # Aggregates, value objects, domain events
│   ├── policy/                      # Domain policies and invariants
│   └── repository/                  # Domain repository ports
├── application/
│   ├── command/                     # Command use cases
│   ├── query/                       # Query use cases
│   ├── port/
│   │   ├── in/                      # Use case interfaces
│   │   └── out/                     # Provider, persistence, bus, cache ports
│   └── service/                     # Application orchestration
├── adapter/
│   ├── in/
│   │   └── web/                     # Controllers, request validation, SSE adapters
│   └── out/
│       ├── kafka/                   # Event publishing/consuming
│       ├── postgres/                # OLTP persistence adapters
│       ├── clickhouse/              # Analytics persistence adapters
│       ├── redis/                   # Cache/cursor/cancellation adapters
│       └── provider/                # LLM provider adapters
├── config/                          # Spring configuration
└── observability/                   # Metrics, tracing, structured log helpers
```

Dependency rule:

- `domain` depends on no framework.
- `application` depends on `domain` and port interfaces.
- `adapter` depends inward on `application`.
- `config` wires adapters to ports.
- Shared libraries must not cause domain packages to depend on Spring, Kafka, Redis, or database APIs.

## Frontend Structure

```text
apps/web
├── app/                             # Next.js App Router routes
├── components/                      # Reusable Mantine components
├── features/
│   ├── requests/                    # Request search and detail workflows
│   ├── conversations/               # Conversation trace workflows
│   ├── analytics/                   # Dashboard widgets and query state
│   └── settings/                    # Provider/project configuration
├── lib/
│   ├── api/                         # Generated API clients and fetch wrappers
│   ├── telemetry/                   # Browser tracing and client metrics
│   └── validation/                  # Runtime validation helpers
└── tests/
```

Frontend dependency rule:

- UI components must not embed backend endpoint strings directly.
- Feature modules consume typed clients generated from OpenAPI contracts.
- Trace context must be propagated from browser requests to backend services.

## Contract Structure

```text
libs/contracts
├── openapi/
│   ├── inference-gateway.v1.yaml
│   ├── analytics-query.v1.yaml
│   └── conversation.v1.yaml
└── events/
    ├── inference-requested.v1.schema.json
    ├── conversation-created.v1.schema.json
    ├── conversation-message-appended.v1.schema.json
    └── conversation-cancelled.v1.schema.json
```

Contract rules:

- Contracts are versioned before implementation.
- Services must generate or validate server/client code from contracts.
- Breaking changes require a new major version and ADR or spec update.
- Event schemas must define compatibility expectations and replay behavior.

## Test Structure

Each service should include:

- Unit tests for domain and application logic.
- WebFlux controller tests for validation, error handling, and SSE behavior.
- Contract tests against OpenAPI.
- Integration tests with PostgreSQL, ClickHouse, Kafka, and Redis using containerized dependencies.
- Observability tests verifying trace attributes, key metrics, and structured log fields for critical paths.

## Documentation Ownership

- `docs/roadmap.md` tracks phase-level delivery.
- `docs/specs` tracks phase and feature-level specifications.
- `docs/architecture` tracks diagrams, contracts, package structures, and deployment topology.
- `docs/adr` records decisions that are difficult to reverse or have broad architectural impact.
