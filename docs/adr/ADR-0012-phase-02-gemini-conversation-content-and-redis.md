# ADR-0012: Phase 2 Gemini, Conversation Content, and Redis Decisions

## Context

Phase 2 needs concrete implementation decisions before the inference gateway can be built. The user approved Gemini as the first real provider, implicit conversation creation, Redis usage, and the PostgreSQL entity set. A production decision is also required for whether raw prompt and completion content should be persisted.

## Decision

Use Gemini as the first real provider adapter in phase 2. The new-conversation inference stream request must not accept `conversationId`; the gateway creates a new UUID conversation for every `POST /v1/inference/stream` request and assigns a title suitable for UI listing. Continue existing conversations through `POST /v1/conversations/{conversationId}/messages/stream`, where the path parameter identifies the parent conversation, the request body contains only the new turn, and the gateway loads prior persisted messages for provider context. Redis is required for active stream coordination and cancellation state. Persist raw prompt and completion content in `conversation_message`, protected by retention, redaction, and access-control rules; do not log raw content or publish raw content to Kafka by default. Include `conversation_message` in phase 2 and exclude `inference_stream_event`. `conversation_message` belongs to the `conversation` aggregate, so it keeps a required foreign key to `conversation` and does not duplicate `tenant_id` or `project_id`; tenant/project scope is resolved through the parent conversation.

## Alternatives Considered

- Use a different first provider.
- Accept client-supplied conversation ids.
- Add optional `conversationId` to `POST /v1/inference/stream` for continuation.
- Require clients to create conversations explicitly before inference.
- Persist only content hashes and token counts.
- Duplicate `tenant_id` and `project_id` on `conversation_message`.
- Keep cancellation state only in PostgreSQL and process memory.
- Persist every stream event in PostgreSQL for replay.

## Tradeoffs

- Gemini support aligns phase 2 with the selected provider, but implementation must handle Gemini-specific streaming, safety metadata, token accounting, and cancellation semantics.
- Backend-owned conversation creation simplifies first-turn clients and prevents clients from binding new requests to unauthorized conversations, but the gateway must generate stable titles and avoid duplicate conversations under idempotent retries.
- A separate continuation endpoint keeps start and continue semantics explicit, but it adds one API surface and requires server-side context assembly.
- Persisting raw prompt and completion content makes the product useful for production debugging, audits, and conversation UI, but it introduces privacy, retention, encryption, and authorization obligations.
- Keeping tenant/project scope only on `conversation` avoids drift between parent and child rows, but scoped message reads and retention jobs must join through `conversation`.
- Redis improves cross-instance cancellation and active-stream coordination, but adds operational dependency and test surface.
- Deferring `inference_stream_event` avoids high write volume and token-level storage risk, but phase 2 will not provide full token stream replay.

## Consequences

- The phase 2 OpenAPI request contract excludes `conversationId` from stream request bodies; continuation uses the conversation id path parameter.
- The gateway must create `conversation` records and `conversation_message` records inside the inference workflow.
- Continuation requests must validate conversation ownership, append new messages, load existing messages for provider context, and create a new `inference_request` under the same conversation.
- `conversation_message` rows must reference an existing `conversation`; they do not store duplicate tenant/project fields.
- Message content storage must be designed with redaction hooks and future encryption support.
- Kafka lifecycle events should carry content hashes and metadata, not raw prompt or completion content.
- Redis integration and tests are required in phase 2.
- Stream replay from persisted events is deferred to a later phase.
