# ADR-0008: Multi-Provider LLM Adapter Model

## Context

The platform must support multiple LLM providers with different APIs, streaming protocols, cancellation behavior, model metadata, rate limits, token accounting, and error formats.

## Decision

Introduce a provider adapter model. The inference gateway will depend on a provider port that exposes normalized operations for streaming inference, cancellation, capability discovery, usage metadata, and error normalization. Provider-specific SDKs and protocols live only in outbound adapters.

## Alternatives Considered

- Implement each provider directly in API controllers.
- Normalize only after persistence.
- Use a single third-party proxy library for all providers.
- Support only one provider until the product matures.

## Tradeoffs

- Adapter abstractions require careful design to avoid hiding meaningful provider differences.
- Normalization simplifies APIs, analytics, and tests.
- Some provider capabilities will remain optional or best-effort.
- A third-party proxy could speed up provider support but may limit observability and cancellation fidelity.

## Consequences

- Provider adapters must declare capabilities, supported models, streaming support, cancellation support, and usage metadata fidelity.
- Provider errors must map to stable platform error codes.
- Provider integration tests should use mock servers and, where safe, controlled live-provider tests.
- Adding a new provider requires contract, observability, and documentation updates.

