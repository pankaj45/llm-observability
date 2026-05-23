# ADR-0017: Chatbot UI — Routing, SSE Parsing, and UX Design

## Context

The platform already has a fully implemented inference gateway (Phase 2), conversation
continuity APIs (Phase 4), and an operator analytics dashboard on `/` (Phase 5). The
assignment requires a user-facing chatbot as the primary deliverable. All backend APIs are
implemented and contract-validated; only the frontend work remains.

Key design questions for this phase:

1. Should the chatbot be a new page in the existing app or a separate Next.js application?
2. Where should the chatbot live relative to the existing analytics dashboard?
3. Should the browser's `EventSource` API or `fetch` with `ReadableStream` be used for SSE?
4. How should the UI handle `tenantId` / `projectId` without an auth system?
5. How should conversation resume work given that the backend owns conversation state?
6. How should a cancelled or failed mid-stream response be displayed?

## Decision

### 1. Routing within the existing app — chatbot at `/`, analytics at `/analytics`

The chatbot is added as a new page inside `apps/web` rather than a new application. The
analytics dashboard is relocated from `/` to `/analytics`. A shared navigation bar in the
root `layout.tsx` provides links between the two views.

### 2. Model selector; provider fixed to `gemini`

The UI shows a model dropdown (`gemini-1.5-pro`, `gemini-1.5-flash`). The provider is always
sent as `gemini` because the `InferenceStreamRequest` OpenAPI contract uses `const: gemini`.
This reflects the current platform capability and keeps the UI honest about what the backend
actually supports.

### 3. `fetch` + `ReadableStream` for SSE — not `EventSource`

The browser's `EventSource` API supports only GET requests. The inference gateway requires a
POST body (`messages`, `provider`, `model`, `idempotencyKey`). The UI uses `fetch()` with
streaming body reading and manually parses SSE frames (`id:`, `event:`, `data:` lines).

### 4. Hardcoded tenant/project defaults with a settings panel

Without an authentication provider (deferred to Phase 6), `tenantId` and `projectId` cannot
be derived from a user session. The UI defaults to `tenant-a` / `project-a` — matching the
seeded data from Phase 2 and the analytics dashboard. A collapsible settings panel allows
overriding these values; they are persisted to `localStorage`.

### 5. Load full message history on resume

When the user selects a conversation in the sidebar, the UI calls
`GET /v1/conversations/{id}/messages` to load the full persisted history. This gives the
user accurate context without requiring the backend to maintain client-side state. Continuation
requests send only the new user turn to `POST /v1/conversations/{id}/messages/stream`; the
backend assembles the full context from PostgreSQL for the provider call.

### 6. Partial text with `CANCELLED` or `FAILED` badge for terminated streams

When the gateway emits `request.cancelled` or `request.failed`, the UI preserves any partial
text already rendered from `token.delta` events and overlays a status badge. This matches the
backend behaviour: `InferenceGatewayService` persists partial assistant messages with
`partial: true` in metadata when content was emitted before termination.

## Alternatives Considered

### Routing alternatives

**Separate `apps/chatbot` application (rejected)**
Would require a new port, a new Docker image, a new Dockerfile, and a new docker-compose
service. The chatbot and analytics are part of the same product surface and should share the
same binary.

**Keep analytics at `/`, put chatbot at `/chat` (rejected)**
The analytics dashboard is an operator tool; the chatbot is the end-user product and the
primary assignment deliverable. Making the chatbot the homepage is more correct for the
assignment context.

### SSE transport alternatives

**`EventSource` API (rejected)**
Does not support POST bodies. Would require converting the inference start to a GET with a
query string, which would expose message content in server logs and URLs.

**WebSocket (rejected)**
Requires server-side WebSocket support. The gateway is built on WebFlux SSE; switching to
WebSocket would be a backend change outside this phase's scope.

### Tenant/project alternatives

**URL path segments (rejected)**
`/chat/tenant-a/project-a` makes the URL fragile for the demo and requires routing changes.
The settings panel pattern already exists in the analytics dashboard.

**Modal on first load (rejected)**
Adds friction for the demo. Hardcoded defaults are sufficient for the assignment context and
match the analytics dashboard UX.

## Tradeoffs

- Using `fetch` + manual SSE parsing is more code than `EventSource`, but correctly handles
  POST bodies and gives full control over error handling and reconnect behaviour.
- Not supporting markdown rendering in bubbles keeps the implementation simple but makes
  code-heavy responses harder to read. Can be added incrementally.
- Loading full message history on resume is one extra API call per conversation selection
  but provides accurate context and does not require the frontend to maintain local message
  state across sessions.
- Defaulting to `tenant-a` / `project-a` is only safe in a development context. Production
  would derive tenant from an authenticated JWT — this is Phase 6 work.

## Consequences

- `apps/web/app/page.tsx` is replaced with the chatbot UI.
- `apps/web/app/analytics/page.tsx` is created with the analytics dashboard content.
- `apps/web/app/layout.tsx` gains a shared navigation bar.
- The chatbot requires `NEXT_PUBLIC_INFERENCE_API_BASE` (default: `http://localhost:8080`).
- Any future addition of a second provider requires updating the model dropdown and relaxing
  the `const: gemini` constraint in the OpenAPI contract and the backend validator.
- Authentication work (Phase 6) must replace the hardcoded tenant/project with JWT-derived
  values; the settings panel becomes optional for privileged operator access only.
