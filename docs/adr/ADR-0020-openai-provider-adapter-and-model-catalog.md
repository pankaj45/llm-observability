# ADR-0020: OpenAI Provider Adapter and Model Catalog

## Context

The inference gateway already isolates provider-specific behavior behind the `ProviderClient` port and has Gemini as the first real provider. Adding OpenAI should prove the multi-provider boundary without changing domain code, controller semantics, or lifecycle persistence. OpenAI's current platform guidance recommends the Responses API for streaming, and the current frontier catalog lists `gpt-5.5` and `gpt-5.4` as the newest general-purpose model IDs.

## Decision

Add an `openai` provider adapter in `services/inference-gateway` that calls `POST /v1/responses` with `stream: true`, normalizes OpenAI SSE events into `ProviderStreamChunk`, and reads credentials from environment-backed Spring properties. Seed the model catalog with `gpt-5.5` and `gpt-5.4`, both enabled for streaming. Keep OpenAI cancellation unsupported in phase 2 because the gateway's current cancellation port only receives the platform request id, while OpenAI cancellation would require tracking provider response ids across stream lifecycle state.

## Alternatives Considered

- Use Chat Completions streaming instead of Responses API.
- Add an OpenAI SDK dependency instead of using WebClient.
- Store OpenAI responses server-side by leaving the provider default `store` behavior enabled.
- Implement provider cancellation immediately by expanding stream state.

## Tradeoffs

- Responses API keeps the adapter aligned with OpenAI's streaming guidance, but its event model requires provider-specific normalization.
- WebClient avoids a provider SDK dependency and preserves the existing adapter pattern, but it requires hand-maintained request and SSE parsing code.
- Setting `store: false` reduces third-party retention exposure, but disables OpenAI-side response retrieval for debugging.
- Deferring provider-native cancellation preserves the existing port shape, but OpenAI cancellation remains best-effort through stream disposal only.

## Consequences

- The OpenAPI request contract accepts both `gemini` and `openai` as provider keys.
- Operators can select `openai` with model `gpt-5.5` or `gpt-5.4` after setting `OPENAI_API_KEY`.
- Domain and application services remain provider-agnostic.
- Future provider-native cancellation support needs a small lifecycle-state extension to retain provider response ids safely without exposing secrets or raw content.
