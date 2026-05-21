# ADR-0001: Spec-Driven Monorepo Governance

## Context

The platform spans frontend, backend services, contracts, infrastructure, and observability. Requirements include strict spec-driven development, continuous architecture documentation, OpenAPI contracts, event schemas, ADRs, tests, and deployment documentation.

## Decision

Use a single monorepo governed by specs, ADRs, and contract-first development. Application code must not be implemented until the relevant phase or feature spec exists. Shared contracts will live under `libs/contracts`, architecture documentation under `docs/architecture`, phase and feature specs under `docs/specs`, and ADRs under `docs/adr`.

## Alternatives Considered

- Polyrepo split by service.
- Docs-only repository with separate implementation repositories.
- Implement services first and document after the fact.

## Tradeoffs

- A monorepo simplifies contract governance, cross-service refactoring, local development, and CI visibility.
- A monorepo requires discipline to prevent tight coupling between services.
- Spec-first development slows the first code milestone but reduces architectural drift and rework.

## Consequences

- Every milestone must update README, architecture docs, and setup instructions.
- CI must validate contracts and documentation references in addition to code.
- Shared libraries must have explicit dependency rules to preserve service boundaries.
- Future repository structure must be created incrementally as implementation phases begin.

