# ADR-0002: Clean Architecture and Domain Boundaries

## Context

The backend must support streaming inference, provider abstraction, event-driven persistence, analytics, cancellation, resumable conversations, and observability without becoming framework-bound or provider-bound.

## Decision

Use clean architecture with domain-driven boundaries in every backend service. Domain models and policies live in framework-free packages. Application services orchestrate use cases through ports. Adapters implement HTTP, Kafka, database, Redis, provider, and observability integrations.

## Alternatives Considered

- Layered Spring MVC-style architecture organized primarily by technical concern.
- Transaction-script services with controllers directly calling repositories and provider clients.
- Shared platform service with all domains in one deployable.

## Tradeoffs

- Clean architecture adds more files and interfaces than a simple layered service.
- Ports and adapters improve testability and provider/database substitution.
- Domain boundaries require upfront modeling effort and ongoing review.

## Consequences

- Domain packages must not depend on Spring, Kafka, Redis, PostgreSQL, ClickHouse, or provider SDKs.
- Provider-specific logic belongs in outbound adapters.
- API request/response models must not become domain models by default.
- Tests should focus heavily on domain invariants and application use cases before adapter integration tests.

