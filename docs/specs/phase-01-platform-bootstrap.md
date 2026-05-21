# Phase 01 Specification: Platform Bootstrap

## Goals

- Create a buildable monorepo foundation for the platform.
- Establish backend service shells for the planned deployable containers.
- Establish a Next.js + Mantine frontend shell.
- Add contract directories and validation for OpenAPI and event schemas.
- Add local development, Docker, CI, health, metrics, and observability scaffolding.

## Scope

- Root Maven parent build for Spring Boot services.
- Root npm workspace build for frontend and contract validation tooling.
- Spring Boot 3 / Java 21 / WebFlux shells for:
  - `inference-gateway`
  - `ingestion-worker`
  - `analytics-query`
- Actuator health and Prometheus metrics endpoints for each backend service.
- R2DBC PostgreSQL and Flyway dependencies wired into backend service baselines.
- OpenTelemetry bridge dependencies and OTLP endpoint configuration.
- Dockerfiles for every service.
- Docker Compose topology for local dependencies and services.
- CI workflow for backend tests, frontend type checking, and contract validation.

## Non-Goals

- No conversation domain implementation in this phase.
- No inference provider integration.
- No Kafka producer or consumer implementation.
- No PostgreSQL, ClickHouse, or Redis business schema migrations.
- No authentication or authorization implementation.
- No production Helm chart implementation.

## Ambiguities

- Whether the conversation service should replace the roadmap's platform-bootstrap phase.
- Whether Maven or Gradle should be the Java build system.
- Whether backend services should be split immediately or started as one service.
- Whether local development should build service images from source or require pre-built jars.
- Whether business migrations should exist before domain aggregates are specified.

## Assumptions

- Phase 1 follows the roadmap's `Platform Bootstrap` milestone rather than implementing conversation behavior.
- Maven is used for Java build orchestration.
- Backend packages use `com.llmobservability.platform` as the root namespace.
- Three backend deployables are created now because they match the existing container architecture.
- Flyway is added as baseline infrastructure, but domain migrations begin with the first domain feature.
- R2DBC is used for reactive PostgreSQL repositories; Flyway uses the JDBC PostgreSQL driver for migrations.
- OpenTelemetry is wired through Micrometer tracing and OTLP configuration.

## Architecture Decisions

- [ADR-0009: Maven and npm Workspace Bootstrap](../adr/ADR-0009-maven-npm-workspace-bootstrap.md)
- [ADR-0010: R2DBC Repositories with Flyway Migrations](../adr/ADR-0010-r2dbc-repositories-with-flyway-migrations.md)

## Implementation Plan

1. Create root build orchestration with Maven and npm workspaces.
2. Add Spring Boot service modules with WebFlux, Actuator, validation, R2DBC, Flyway, Prometheus, and OpenTelemetry dependencies.
3. Add service Dockerfiles with non-root runtime users.
4. Add Next.js + Mantine app shell with TypeScript type checking.
5. Add OpenAPI and event schema contract directories with validation script.
6. Add Docker Compose topology for local dependencies, services, Prometheus, Grafana, and OpenTelemetry Collector.
7. Add CI workflow.
8. Update README, local development docs, architecture docs, and ADR index.

## Acceptance Criteria

- `mvn test` runs backend service tests.
- `npm run contracts` validates OpenAPI and event schema files.
- `npm run typecheck --workspace apps/web` validates the frontend shell.
- Each backend service exposes `/actuator/health` and `/actuator/prometheus`.
- Every service has a Dockerfile.
- Docker Compose defines PostgreSQL, Redis, ClickHouse, Kafka, OpenTelemetry Collector, Prometheus, Grafana, frontend, and backend services.
- README and setup docs describe the implemented commands and ports.

## Risks

- Dependency downloads can fail in restricted network environments.
- Docker Compose builds can be slower than local process-based development.
- Splitting services early creates more scaffolding, but it protects the planned boundaries.
- Flyway and R2DBC use different PostgreSQL drivers, so connection settings must stay explicit.

## Test Strategy

- Backend Spring Boot smoke tests verify health and Prometheus metrics endpoints.
- Contract validation script verifies OpenAPI shape and JSON Schema compilability.
- Frontend TypeScript check validates the app shell.
- CI runs backend tests, frontend type checking, and contract validation.

## Observability Requirements

- Backend services expose Prometheus metrics through Actuator.
- Backend services include trace and span ids in console log patterns.
- Backend services configure OTLP trace export endpoint through environment variables.
- Local Compose includes OpenTelemetry Collector, Prometheus, and Grafana wiring.

