# Engineering Standards

## Spec-Driven Development

Before implementation, every feature must have a spec covering:

- Goals and non-goals.
- Scope and affected services.
- Assumptions and unresolved ambiguities.
- API contract impact.
- Event schema impact.
- Persistence impact.
- Observability impact.
- Security and privacy impact.
- Acceptance criteria.
- Risks and rollback strategy.
- Test plan.

Specs live in `docs/specs`. Large or irreversible decisions also require an ADR in `docs/adr`.

## Architecture Standards

- Use clean architecture and domain-driven boundaries.
- Keep domain logic independent of Spring, Kafka, Redis, PostgreSQL, ClickHouse, and HTTP.
- Prefer explicit ports and adapters over implicit framework coupling.
- Model provider-specific behavior behind adapter interfaces.
- Treat Kafka events as integration contracts, not internal implementation details.
- Do not introduce shared libraries that hide service boundaries or create circular dependencies.

## API Standards

Every API must include:

- OpenAPI specification before implementation.
- Request and response validation.
- Deterministic error response schema.
- Authentication and authorization behavior.
- Idempotency behavior where retries are expected.
- Pagination, filtering, and sorting semantics where collection endpoints exist.
- Trace propagation and documented metrics.
- Unit, contract, and integration tests.

Error responses should use a consistent envelope:

```json
{
  "error": {
    "code": "validation.invalid_request",
    "message": "Request validation failed.",
    "details": [],
    "traceId": "string"
  }
}
```

## Event Standards

Every event must include:

- Stable event name.
- Schema version.
- Event id.
- Occurred-at timestamp.
- Producer.
- Tenant and project identifiers.
- Correlation id and trace context.
- Idempotency key when replay or retry can occur.

Events must be validated in CI and tested by producers and consumers.

## Backend Standards

- Java 21 and Spring Boot 3.
- Reactive APIs with Spring WebFlux for streaming and high-concurrency request handling.
- Avoid blocking calls on event-loop threads.
- Use explicit timeouts, retries, circuit breakers, and backpressure policies for provider calls and persistence.
- Use Bean Validation or equivalent validation at API boundaries.
- Use structured logs with stable field names.
- Emit OpenTelemetry traces and Prometheus metrics for critical paths.
- Use health, readiness, and liveness endpoints for every service.

## Frontend Standards

- Next.js with TypeScript and Mantine.
- API clients should be generated from OpenAPI or validated against OpenAPI-derived types.
- UI state must cover loading, empty, error, retry, and degraded states.
- User-facing views must propagate trace context to backend calls.
- Accessibility is required for dashboard and operational workflows.

## Testing Standards

Required test layers:

- Domain unit tests.
- Application use case tests.
- Adapter tests for provider, Kafka, persistence, and cache integrations.
- API contract tests.
- SSE streaming tests, including cancellation and reconnect behavior.
- Integration tests with realistic dependencies.
- Frontend unit and interaction tests.
- End-to-end tests for critical user journeys once the UI exists.

Do not merge a feature without tests matching its blast radius.

## Observability Standards

Every service must emit:

- Structured logs.
- OpenTelemetry traces.
- Prometheus metrics.
- Health, readiness, and liveness status.

Required trace attributes for inference flows:

- `tenant.id`
- `project.id`
- `conversation.id`
- `inference.request_id`
- `llm.provider`
- `llm.model`
- `llm.operation`
- `streaming.enabled`

High-cardinality values such as prompts, completions, raw user identifiers, and request bodies must not be emitted as metric labels.

## Security and Privacy Standards

- Treat prompts, completions, provider responses, and conversation metadata as sensitive data.
- Redact secrets and credentials from logs.
- Encrypt sensitive data in transit.
- Define retention and deletion behavior before implementing long-term storage.
- Use least-privilege credentials per service.
- Validate tenant and project authorization at every API boundary.

## Change Management

- Each milestone updates README, architecture docs, setup docs, and relevant ADRs.
- Breaking API or event changes require versioning and migration notes.
- Production-impacting changes require rollback strategy and operational notes.

