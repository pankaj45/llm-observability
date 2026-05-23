# Phase 07 Specification: Chatbot UI

## Implementation Status

Status as of 2026-05-23: approved for implementation. This phase adds the user-facing chatbot
as the root page (`/`) of `apps/web` and relocates the operator analytics dashboard to
`/analytics`. All backend APIs were already implemented and contract-validated in Phases 2–5.

## Goals

- Provide a user-facing chatbot UI that satisfies the assignment's primary deliverable.
- Support multi-turn conversations using the existing Phase 2 and Phase 4 streaming APIs.
- Implement all three frontend bonus requirements: list conversations, resume a conversation,
  cancel an active stream.
- Preserve the existing operator analytics dashboard at `/analytics`.
- Document routing, SSE parsing, and UX decisions in an ADR.

## Scope

- New chatbot page at `/` in `apps/web`.
- Conversation sidebar listing conversations from `GET /v1/conversations`.
- SSE streaming rendering using `fetch` + `ReadableStream` (not `EventSource`, which cannot POST).
- Resume a conversation by selecting it in the sidebar and loading its history from
  `GET /v1/conversations/{id}/messages`.
- Stop button during active streaming that calls `DELETE /v1/conversations/{id}/stream`.
- Model selector (Gemini model variants); provider is always `gemini`.
- Settings panel for `tenantId` and `projectId` with hardcoded defaults, persisted to
  `localStorage`.
- Relocation of the analytics dashboard to `apps/web/app/analytics/`.
- Shared navigation bar in the root layout.
- ADR-0017 documenting all decisions.
- README rewrite covering setup, architecture, schema, tradeoffs, and future work.

## Non-Goals

- No backend changes. All required APIs are implemented.
- No authentication provider. Tenant/project parameters are handled the same way as the
  analytics dashboard.
- No token-level replay or SSE reconnect resumption (Phase 4 defers this).
- No user account management.
- No markdown rendering beyond plain text in message bubbles (can be added later).
- No mobile-responsive layout optimisation beyond what Mantine provides.

## Resolved Decisions

See ADR-0017 for the full decision record. Summary:

1. Chatbot UI lives at `/`; analytics at `/analytics`. Evaluated alternatives are documented in
   ADR-0017.
2. Model selector shows Gemini model variants; provider is always `gemini` because the
   `InferenceStreamRequest` contract uses `const: gemini`.
3. Conversation resume loads full message history from
   `GET /v1/conversations/{id}/messages` on sidebar selection.
4. Tenant and project IDs default to `tenant-a` / `project-a` with a collapsible settings
   panel; values are persisted to `localStorage`.
5. Partially streamed text from a cancelled request is shown with a `CANCELLED` badge.
6. The Stop button is visible only while a stream is actively generating; it disappears once
   the stream completes, fails, or is cancelled.

## API Wiring

No backend changes are required. The chatbot uses the following existing endpoints:

| Action | Method | Endpoint |
|---|---|---|
| Start new conversation | POST | `/v1/inference/stream` |
| Continue conversation | POST | `/v1/conversations/{id}/messages/stream` |
| List conversations | GET | `/v1/conversations` |
| Load message history | GET | `/v1/conversations/{id}/messages` |
| Cancel active stream | DELETE | `/v1/conversations/{id}/stream` |

The inference gateway base URL is read from `NEXT_PUBLIC_INFERENCE_API_BASE` (default:
`http://localhost:8080`). The analytics query base URL continues to be read from
`NEXT_PUBLIC_ANALYTICS_API_BASE` (default: `http://localhost:8081`).

## SSE Event Wire Format

The gateway emits `text/event-stream` with a JSON body on each event. The event name is in the
SSE `event:` field and `conversationId` / `requestId` are top-level fields on every event.

```
id: <requestId>:<sequence>
event: <wireName>
data: { "id": "...", "type": "...", "requestId": "...", "conversationId": "...",
        "traceId": "...", "sequence": N, "occurredAt": "...", "data": { ... } }
```

| Wire event name | UI action |
|---|---|
| `request.accepted` | Extract `conversationId` for a new conversation; set streaming state |
| `token.delta` | Append `data.delta` (text chunk) to the assistant bubble |
| `usage.delta` | No text; update token count from `data.outputTokens` |
| `request.completed` | Finalise message; update token counts; clear streaming state |
| `request.cancelled` | Mark message with `CANCELLED` badge; clear streaming state |
| `request.failed` | Mark message with `FAILED` badge and `data.errorCode`; clear state |
| `heartbeat` | No UI change; reset connection timeout |

## UI Layout

```
┌── Nav: [💬 Chat]  [📊 Analytics] ──────────────────────────────────┐
│ ┌─ Sidebar ───────┐ ┌─ Chat area ────────────────────────────────┐ │
│ │  + New Chat     │ │                                            │ │
│ │  ─────────────  │ │  [user bubble — right]                     │ │
│ │  [conv title 1] │ │  [assistant bubble — left, streaming...]   │ │
│ │  [conv title 2] │ │                                            │ │
│ │  ...            │ │  ──────────────────────────────────────    │ │
│ │                 │ │  Model ▼  [ type a message... ] [Send/Stop]│ │
│ │  ─────────────  │ └────────────────────────────────────────────┘ │
│ │  ⚙ Settings    │                                                 │
│ └─────────────────┘                                                 │
└─────────────────────────────────────────────────────────────────────┘
```

## Idempotency Key

The backend requires a unique `idempotencyKey` on every inference request. The UI generates
a `crypto.randomUUID()` per send action. This is not persisted; retrying by re-sending
generates a new key.

## Error Handling

| Scenario | UI behaviour |
|---|---|
| Network error on send | Show error toast; input re-enabled |
| `request.failed` SSE event | Failed badge + errorCode shown in bubble |
| 400 from cancel endpoint | Show error toast; stream already terminated |
| Conversation list load fails | Show inline error, retry button |
| History load fails | Show inline error in chat area |

## Observability Impact

No backend changes are required. The chatbot adds no new server-side metrics, traces, or
events. Future phases may instrument client-side errors via OpenTelemetry JS.

## Acceptance Criteria

- `npm run typecheck --workspace apps/web` passes.
- `npm run contracts` passes.
- Navigating to `http://localhost:3000` shows the chatbot.
- Typing a message and pressing Send starts a new conversation; text streams in token by token.
- The conversation appears in the left sidebar after the stream completes.
- Clicking a sidebar conversation loads the full message history and allows continuation.
- Clicking Stop during a stream cancels it; partial text is shown with a `CANCELLED` badge.
- Navigating to `http://localhost:3000/analytics` shows the analytics dashboard unchanged.
- The settings panel allows overriding tenantId and projectId; values persist across reloads.

## Test Strategy

- TypeScript typecheck covers all API call signatures and SSE event parsing types.
- `npm run contracts` validates the OpenAPI contracts referenced by the UI.
- Manual walkthrough against the local `make dev` stack.
- No new automated frontend tests are added in Phase 7; this matches the existing pattern in
  the analytics dashboard.
