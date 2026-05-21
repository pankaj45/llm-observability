# ADR-0009: Maven and npm Workspace Bootstrap

## Context

Phase 1 needs a buildable monorepo with Java backend services, a TypeScript frontend, contract validation, CI, and Docker packaging. The repository must support multiple Spring Boot services and a Next.js application while preserving service boundaries.

## Decision

Use a Maven multi-module parent for backend services and npm workspaces for frontend and contract tooling. Backend services live under `services`, the frontend app lives under `apps/web`, and shared contracts live under `libs/contracts`.

## Alternatives Considered

- Gradle multi-project build for Java services.
- Separate repositories per service.
- pnpm or yarn workspaces for frontend tooling.
- A single root build tool for all languages.

## Tradeoffs

- Maven is widely understood in Spring Boot teams and keeps Java build behavior predictable.
- npm workspaces avoid adding an additional package manager while still supporting frontend workspace scripts.
- Separate Java and Node build systems require CI to run both paths explicitly.
- Maven is less flexible than Gradle for complex cross-language orchestration, but phase 1 does not need that complexity.

## Consequences

- CI must run Maven backend tests and npm frontend/contract checks.
- Dependency caching should be configured separately for Maven and npm.
- Java shared code must be introduced intentionally as modules, not by leaking code between service directories.
- Root commands such as `make test` orchestrate the build paths without hiding each ecosystem's native tooling.

