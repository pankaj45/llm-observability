# ADR-0003: Reactive WebFlux and SSE Streaming

## Context

The platform must stream LLM responses, support cancellation, handle many concurrent long-lived requests, and expose resumable conversation events. Blocking request-per-thread architecture is a poor fit for high-concurrency streaming.

## Decision

Use Spring Boot 3 with Spring WebFlux for backend HTTP APIs and Server-Sent Events for streaming inference and conversation event delivery.

## Alternatives Considered

- Spring MVC with blocking IO.
- WebSockets for all streaming.
- gRPC streaming.
- Polling-based streaming simulation.

## Tradeoffs

- WebFlux supports non-blocking streaming and backpressure-aware composition but requires careful avoidance of blocking calls.
- SSE is simpler than WebSockets for one-way server-to-client streams and works well with HTTP infrastructure.
- SSE does not provide bidirectional messaging, so cancellation uses a separate HTTP endpoint.
- gRPC streaming is strong for service-to-service APIs but less convenient for browser clients.

## Consequences

- Services must isolate blocking database/provider clients on appropriate schedulers or use reactive clients.
- Streaming tests must verify chunk ordering, cancellation, disconnects, and error behavior.
- APIs must document SSE event names, cursor behavior, heartbeat behavior, and retry semantics.
- Operational dashboards must track active streams, stream duration, cancellation latency, and reconnects.

