# ADR-0010: R2DBC Repositories with Flyway Migrations

## Context

Backend services must use Spring WebFlux and reactive repositories while PostgreSQL schema evolution must be controlled through Flyway migrations. Flyway uses JDBC, while reactive repositories use R2DBC.

## Decision

Use Spring Data R2DBC for reactive PostgreSQL access and Flyway for schema migrations. Services include both the R2DBC PostgreSQL driver and JDBC PostgreSQL driver. Flyway is disabled by default in service shell tests and enabled explicitly in environments that provide migration database credentials.

## Alternatives Considered

- JDBC repositories with WebFlux controllers.
- R2DBC-only migrations.
- Liquibase instead of Flyway.
- Application-created schemas without migration tooling.

## Tradeoffs

- R2DBC keeps application data access aligned with WebFlux but has a smaller ecosystem than JDBC.
- Flyway is mature and operationally familiar, but it requires JDBC connectivity even in reactive services.
- Disabling Flyway by default in shell tests keeps bootstrap tests fast; domain implementation phases must add migration integration tests.
- Maintaining both JDBC and R2DBC connection settings adds configuration surface area.

## Consequences

- Domain features must define Flyway migrations before repository implementation.
- Integration tests for persistence must run against PostgreSQL, not in-memory substitutes.
- Runtime configuration must clearly separate `spring.r2dbc.*` from Flyway/JDBC settings.
- Repository code must avoid blocking calls on WebFlux event-loop threads.

