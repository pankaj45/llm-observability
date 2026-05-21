# Phase 02 Specification: Inference Gateway MVP

## Implementation Status

Status as of 2026-05-22: implemented for the inference gateway service, excluding Docker image validation and live external-provider testing.

Implemented:

- WebFlux `POST /v1/inference/stream` SSE API.
- Backend-owned UUID conversation creation and title generation.
- Rejection of client-supplied `conversationId` in stream requests.
- Gemini provider adapter through the provider port.
- Redis active stream and cancellation state.
- Kafka lifecycle event publisher for requested, completed, cancelled, and failed events.
- Flyway migration for the approved phase 2 PostgreSQL entity set.
- Reactive PostgreSQL adapters using `DatabaseClient`.
- Status and cancellation APIs.
- Focused service, controller, health, metrics, and contract validation tests.

Deferred from runtime verification:

- Docker image build and container-level testing.
- Live Gemini API validation with real credentials.
- PostgreSQL/Kafka/Redis integration tests against running containers.

## Goals

- Implement the first production-grade streaming inference API in `services/inference-gateway`.
- Support one real LLM provider through the multi-provider adapter interface.
- Stream normalized Server-Sent Events to clients.
- Support cancellation for active inference requests.
- Emit durable lifecycle events for observability and future ingestion.
- Persist enough PostgreSQL state to make request status, idempotency, and cancellation reliable.
- Create a backend-owned conversation id for every inference request.
- Persist conversation messages so the UI can show conversation detail and summaries in later phases.
- Keep provider-specific behavior isolated behind outbound adapters.

## Scope

- WebFlux API implementation for streaming inference, request status, and cancellation.
- OpenAPI v1 contract expansion for inference APIs.
- Provider adapter port and first Gemini provider implementation.
- Normalized provider request and response model.
- SSE event model and heartbeat behavior.
- Request validation and deterministic error envelope.
- PostgreSQL Flyway migrations and reactive repositories for the approved phase 2 entity set.
- Kafka lifecycle event publishing.
- Redis-backed active stream and cancellation coordination.
- OpenTelemetry spans, Prometheus metrics, and structured logs for critical paths.
- Unit, contract, integration, streaming, cancellation, and observability tests.

## Non-Goals

- No analytics dashboard implementation.
- No ClickHouse ingestion implementation.
- No conversation summary generation.
- No token-aware context window optimization beyond accepting a request payload already prepared by the caller.
- No provider routing or fallback policy across multiple live providers.
- No billing workflow or tenant chargeback.
- No token stream replay from persisted stream events.
- No `inference_stream_event` table.

## Ambiguities

- Whether token chunks should be published to Kafka for every stream or sampled/disabled initially.
- Authentication model is not implemented yet, so tenant/project trust boundary remains local-development oriented.
- Exact provider credential source is undecided: environment variables, tenant-scoped secrets table, Kubernetes secrets, or external secret manager.
- Exact conversation title generation policy is undecided beyond the phase 2 default.

## Recommended Approach

- Use Gemini as the first real provider.
- Require `tenantId` and `projectId`; do not accept `conversationId` in the `POST /v1/inference/stream` request body.
- Create a UUID conversation for every inference request.
- Assign a conversation title at creation time. The phase 2 default should derive a short title from the first user message when available, with deterministic fallback `New conversation`.
- Persist raw prompt and completion content in `conversation_message` because production observability needs inspectable conversations for debugging, audit, user support, and UI trace workflows.
- Protect persisted content with access-control, retention, and redaction rules. Do not log raw content, use raw content as metric labels, or publish raw content to Kafka by default.
- Publish lifecycle events for requested, completed, cancelled, and failed. Do not publish every token chunk by default in phase 2.
- Use Redis for active stream tracking, cancellation coordination, and short-lived stream state.
- Implement provider credentials through environment variables for phase 2 local/staging, with an ADR required before tenant-scoped secret storage.

## Architecture Decisions

Existing ADRs cover the base architecture:

- [ADR-0003: Reactive WebFlux and SSE Streaming](../adr/ADR-0003-reactive-webflux-sse-streaming.md)
- [ADR-0004: Kafka as Domain Event Bus](../adr/ADR-0004-kafka-domain-event-bus.md)
- [ADR-0008: Multi-Provider LLM Adapter Model](../adr/ADR-0008-multi-provider-llm-adapter-model.md)
- [ADR-0010: R2DBC Repositories with Flyway Migrations](../adr/ADR-0010-r2dbc-repositories-with-flyway-migrations.md)

New phase 2 decision:

- [ADR-0011: Inference Gateway Request Lifecycle Ownership](../adr/ADR-0011-inference-gateway-request-lifecycle-ownership.md)
- [ADR-0012: Phase 2 Gemini, Conversation Content, and Redis Decisions](../adr/ADR-0012-phase-02-gemini-conversation-content-and-redis.md)

## API Contract

The OpenAPI contract in `libs/contracts/openapi/inference-gateway.v1.yaml` must be expanded before implementation.

### `POST /v1/inference/stream`

Starts a streaming inference request.

Required request fields:

- `tenantId`
- `projectId`
- `provider`
- `model`
- `messages`
- `parameters`
- `idempotencyKey`

Optional request fields:

- `metadata`
- `clientRequestId`
- `streamOptions`

Required behavior:

- Validate tenant and project identifiers.
- Reject request bodies that include `conversationId`.
- Create a UUID conversation and title for every request.
- Validate provider and model capabilities.
- Validate message roles and content blocks.
- Validate model parameters with explicit bounds.
- Return SSE stream with stable event ids.
- Emit lifecycle events.
- Persist lifecycle state and conversation messages.

### `DELETE /v1/inference/{requestId}/stream`

Requests cancellation of an active stream.

Required behavior:

- Validate request ownership.
- Mark cancellation requested.
- Attempt provider cancellation where supported.
- Emit `inference.cancelled` if cancellation is accepted.
- Return a deterministic cancellation result for accepted cancellation requests.
- Return deterministic error envelope for unknown, completed, or unauthorized requests.

### `GET /v1/inference/{requestId}`

Reads request status and summary metadata.

Required behavior:

- Return current lifecycle status.
- Include provider, model, timestamps, usage summary, cancellation status, and error summary.
- Exclude raw prompt and completion content from the default status response.
- Include raw prompt and completion content only through conversation/message APIs that enforce authorization and redaction policy.

## SSE Event Contract

SSE events must include:

- `id`: stable stream event id.
- `event`: event type.
- `data.requestId`
- `data.conversationId`
- `data.traceId`
- `data.sequence`
- `data.occurredAt`

Event types:

- `request.accepted`
- `message.delta`
- `token.delta`
- `usage.delta`
- `request.completed`
- `request.cancelled`
- `request.failed`
- `heartbeat`

Rules:

- Event ids must be monotonic within a request stream.
- Heartbeats must be emitted during provider silence to keep intermediaries from closing the connection.
- Client disconnect should stop downstream streaming work when possible.
- Provider errors must be normalized into `request.failed`.

## Event Schemas

Phase 2 must create or expand schemas for:

- `inference.requested`
- `inference.completed`
- `inference.cancelled`
- `inference.failed`

Optional, policy-gated schema:

- `inference.token_streamed`

Required event envelope fields:

- `eventId`
- `eventName`
- `schemaVersion`
- `occurredAt`
- `producer`
- `tenantId`
- `projectId`
- `correlationId`
- `traceparent`
- `idempotencyKey`
- `payload`

## Approved PostgreSQL Entities

The user approved the phase 2 PostgreSQL entity set. Flyway migrations may be written for the entities in this section during phase 2 implementation.

### `llm_provider`

Purpose: provider catalog and operational metadata.

Core fields:

- `id`
- `provider_key`
- `display_name`
- `enabled`
- `supports_streaming`
- `supports_cancellation`
- `created_at`
- `updated_at`

Rationale:

- Keeps provider capability checks out of hard-coded controller logic.
- Allows future provider configuration without changing request schemas.

Tradeoff:

- Adds a catalog table before full provider management UI exists.

### `llm_model`

Purpose: model catalog scoped to providers.

Core fields:

- `id`
- `provider_id`
- `model_key`
- `display_name`
- `context_window_tokens`
- `max_output_tokens`
- `enabled`
- `created_at`
- `updated_at`

Rationale:

- Supports validation of model, max tokens, and context constraints.
- Prepares for token-aware context windows in later phases.

Tradeoff:

- Model catalogs change frequently, so the platform needs update discipline.

### `conversation`

Purpose: minimal authoritative conversation reference for inference requests.

Core fields:

- `id`
- `tenant_id`
- `project_id`
- `status`
- `title`
- `title_source`
- `created_at`
- `updated_at`
- `cancelled_at`

Rationale:

- Phase 2 inference requests need a stable conversation foreign key.
- Implicit conversation creation gives clients a simpler streaming API.
- Titles are needed for UI listing and conversation navigation.
- Enables status validation without implementing full conversation continuity.

Tradeoff:

- Creates part of the conversation domain before the full conversation service phase.
- Title generation must avoid leaking sensitive data into logs and telemetry.

### `conversation_message`

Purpose: stores user, assistant, system, and tool messages associated with a conversation.

Core fields:

- `id`
- `conversation_id`
- `tenant_id`
- `project_id`
- `role`
- `sequence`
- `content`
- `content_hash`
- `estimated_tokens`
- `redaction_state`
- `metadata`
- `created_at`

Rationale:

- Enables traceability, token accounting, UI display, support workflows, and production debugging.
- Provides groundwork for later conversation continuity and context windows.

Tradeoff:

- Raw prompt and completion content are sensitive and require access-control, retention, redaction, and future encryption support.

### `inference_request`

Purpose: authoritative lifecycle state for an inference request.

Core fields:

- `id`
- `tenant_id`
- `project_id`
- `conversation_id`
- `provider_id`
- `model_id`
- `idempotency_key`
- `status`
- `streaming`
- `request_metadata`
- `input_message_count`
- `input_content_hash`
- `output_content_hash`
- `redis_stream_key`
- `created_at`
- `started_at`
- `first_token_at`
- `completed_at`
- `cancelled_at`
- `failed_at`
- `updated_at`

Rationale:

- Supports idempotency, status lookup, cancellation, and lifecycle event reconciliation.

Tradeoff:

- The gateway owns more state in phase 2, but this improves reliability and testability.

### `inference_usage`

Purpose: normalized token and cost metadata for completed requests.

Core fields:

- `id`
- `inference_request_id`
- `input_tokens`
- `output_tokens`
- `total_tokens`
- `provider_reported_units`
- `estimated_cost_amount`
- `estimated_cost_currency`
- `created_at`

Rationale:

- Avoids overloading `inference_request` with provider-specific usage details.
- Gives analytics ingestion a clean source record later.

Tradeoff:

- Cost estimation may be incomplete or provider-specific in phase 2.

### `inference_error`

Purpose: normalized failure details.

Core fields:

- `id`
- `inference_request_id`
- `failure_stage`
- `error_code`
- `provider_error_code`
- `message`
- `retryable`
- `created_at`

Rationale:

- Supports deterministic API errors, status inspection, and provider failure analysis.

Tradeoff:

- Error messages require redaction rules to avoid sensitive data leakage.

### `inference_cancellation`

Purpose: cancellation attempts and outcomes.

Core fields:

- `id`
- `inference_request_id`
- `requested_by`
- `reason`
- `provider_cancellation_attempted`
- `provider_cancellation_succeeded`
- `created_at`
- `resolved_at`

Rationale:

- Captures best-effort cancellation semantics and supports auditability.

Tradeoff:

- Adds write path complexity for a state that may be transient for single-instance deployments.

### Deferred: `inference_stream_event`

Purpose: persisted stream event cursor for replay and debugging.

Core fields:

- `id`
- `inference_request_id`
- `sequence`
- `event_type`
- `content_hash`
- `token_count`
- `occurred_at`

Rationale:

- Enables stream replay and reconnect semantics if phase 2 includes resume-like behavior.

Decision:

- Not included in phase 2.
- Token-level event persistence can create high write volume and content retention risk.
- Redis will hold short-lived active stream state only; durable token stream replay is deferred.

## Final Entity Set

Included in phase 2:

- `llm_provider`
- `llm_model`
- `conversation`
- `conversation_message`
- `inference_request`
- `inference_usage`
- `inference_error`
- `inference_cancellation`

Deferred:

- `inference_stream_event`

Reasoning:

- The final set supports provider/model validation, implicit conversation creation, conversation UI, idempotency, streaming request lifecycle, cancellation, usage accounting, and status lookup.
- Deferring stream-event persistence avoids token-level write amplification and replay semantics before the ingestion pipeline is implemented.

## Package Structure

Implemented package structure for `services/inference-gateway`:

```text
com.llmobservability.platform.inferencegateway
├── domain/model/
├── application/
│   ├── port/in/
│   ├── port/out/
│   └── service/
├── adapter/
│   ├── in/web/
│   └── out/
│       ├── kafka/
│       ├── postgres/
│       ├── provider/
│       │   └── gemini/
│       └── redis/
└── config/
```

Dependency rules:

- Domain code must not depend on Spring, Kafka, R2DBC, Redis, or provider SDKs.
- Provider adapters normalize provider events into domain/application models.
- Web adapters translate domain outcomes into API responses and SSE events.
- Persistence adapters implement ports using reactive repositories.

## Observability Requirements

Traces:

- Span for API request handling.
- Span for request validation.
- Span for idempotency lookup.
- Span for provider invocation.
- Span for Redis active stream registration and cancellation lookup.
- Span for each lifecycle event publish.
- Span for persistence writes.
- Span for cancellation attempts.

Metrics:

- `inference_requests_total`
- `inference_request_duration_seconds`
- `inference_first_token_latency_seconds`
- `inference_active_streams`
- `inference_stream_cancellations_total`
- `inference_provider_errors_total`
- `inference_provider_timeouts_total`
- `inference_tokens_total`
- `kafka_publish_duration_seconds`
- `r2dbc_query_duration_seconds`
- `redis_operation_duration_seconds`

Logs:

- Accepted request.
- Provider stream started.
- First token observed.
- Request completed.
- Request cancelled.
- Request failed.

Required log fields:

- `traceId`
- `correlationId`
- `tenantId`
- `projectId`
- `conversationId`
- `requestId`
- `provider`
- `model`
- `status`

Privacy rule:

- Do not log raw prompts, raw completions, provider credentials, or authorization headers.
- Persisted message content must be redacted or access-controlled for user-facing reads.

## Error Handling

Stable error categories:

- `validation.invalid_request`
- `auth.unauthorized`
- `auth.forbidden`
- `conversation.not_found`
- `provider.unsupported`
- `provider.timeout`
- `provider.rate_limited`
- `provider.unavailable`
- `stream.client_disconnected`
- `stream.cancelled`
- `internal.persistence_error`
- `internal.event_publish_error`

Rules:

- Errors must use the shared error envelope.
- Provider-specific errors must map to stable platform error codes.
- Retryability must be explicit in internal error models and emitted events.
- Sensitive provider response details must be redacted.

## Test Strategy

Unit tests:

- Domain lifecycle transitions.
- Idempotency behavior.
- Cancellation state transitions.
- Provider error normalization.
- Parameter validation.

WebFlux tests:

- Request validation.
- Error envelope.
- SSE event ordering.
- Heartbeat behavior.
- Client disconnect behavior.
- Cancellation endpoint behavior.

Contract tests:

- OpenAPI request and response examples.
- SSE media type and event schema expectations.
- Error response schema.

Integration tests:

- PostgreSQL persistence with Flyway migrations.
- Kafka lifecycle event publishing.
- Provider adapter using mock provider server.
- Gemini adapter using mock Gemini-compatible streaming server.
- Redis active stream and cancellation coordination.

Observability tests:

- Required metrics emitted.
- Trace attributes present with controlled cardinality.
- Structured logs include correlation fields and exclude raw content.

## Acceptance Criteria

- OpenAPI v1 inference contract is expanded and validated in CI.
- `POST /v1/inference/stream` streams normalized SSE events.
- `DELETE /v1/inference/{requestId}/stream` accepts cancellation and produces a stable outcome.
- `GET /v1/inference/{requestId}` returns request status without raw sensitive content.
- Provider adapter interface supports Gemini streaming, cancellation capability reporting, usage metadata, and error normalization.
- Gemini provider adapter passes mock-provider integration tests.
- Gemini provider adapter compiles and is isolated behind the provider port; live and mock-server integration tests are deferred until container/provider verification.
- Kafka lifecycle events are emitted for requested, completed, cancelled, and failed.
- Approved PostgreSQL migrations are implemented with Flyway for the final phase 2 entity set.
- Redis active stream and cancellation state is implemented and tested.
- Every streaming inference request creates a UUID conversation with a UI-ready title.
- Conversation messages persist protected user and assistant content.
- Reactive repository adapters compile against the approved schema; PostgreSQL integration tests are scheduled for the container verification pass.
- Metrics, traces, and structured logs exist for critical paths.
- README, architecture docs, setup docs, and ADRs are updated.

## Risks

- Provider streaming semantics may not map cleanly to the normalized SSE model.
- Cancellation may be best-effort and provider-dependent.
- Persisting too much prompt/completion content could create privacy and compliance risk.
- Persisting every stream chunk could create high write volume before analytics ingestion is ready.
- Idempotency and retries can produce duplicate lifecycle events if event keys are not designed carefully.
- Missing authentication can create false confidence in tenant/project isolation during local development.

## Approval Record

Approved by the user:

- First real provider: Gemini.
- Conversations are always created by the backend with UUIDs for streaming inference requests.
- Conversations have titles for UI display.
- Raw prompt and completion content persistence decision delegated to engineering; decision is to persist protected content in `conversation_message`.
- Redis is required in phase 2.
- Final PostgreSQL entity set is approved.
- `conversation_message` is included.
- `inference_stream_event` is not required and is deferred.
