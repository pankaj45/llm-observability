# ADR-0019: Phase 09 PII Redaction

## Context

`conversation_message` persists raw user prompt content in PostgreSQL. This content may
contain personally identifiable information (PII) such as email addresses, phone numbers,
credit card numbers, Social Security Numbers, IP addresses, and national identity numbers.

The platform already models `RedactionState` on `conversation_message` and always persists
it as `NONE`. No scanning, detection, or redaction occurs before or after persistence.
`ConversationMessage.metadata` is available as a `Map<String, String>` for recording
audit data alongside the message.

The Phase 6 hardening spec explicitly requires "redaction verification" and
"protected-content redaction checks" as release gates. ADR-0012 explicitly states that
"message content storage must be designed with redaction hooks and future encryption
support."

README item 1 explicitly lists PII redaction as a planned improvement: "add regex/NER
scanning on content before persistence."

## Decision

Implement regex-based PII scanning of user messages as a domain outbound port
(`PiiRedactionPort`) before `ConversationMessage` persistence. The adapter
(`RegexPiiRedactionAdapter`) applies per-category compiled patterns and returns a
`PiiRedactionResult` containing the redacted content string and the list of category
names that matched. No raw matched values are returned.

When redaction fires:
- The redacted content (with placeholders such as `[EMAIL]`, `[PHONE]`) is stored in
  `conversation_message.content`.
- `conversation_message.redaction_state` is set to `REDACTED`.
- `conversation_message.metadata` records `redactedCategories` as a JSON array string
  (e.g. `["EMAIL", "PHONE"]`). No raw PII values are stored.
- A structured audit log line is emitted at INFO level (no raw values).
- Metrics counters increment per detected category.

The redacted content is used for all downstream steps: provider context assembly,
conversation history on continuation, and the `ConversationMessage` API read path.
The provider (Gemini) therefore never receives raw PII in user turns.

`input_content_hash` on `inference_request` is computed from the pre-redaction command
message list. This preserves idempotency semantics: the hash identifies the request
intent, not the stored representation.

Initial PII categories: EMAIL, PHONE, CREDIT_CARD, SSN, IP_ADDRESS, AADHAAR.

Redaction is configurable via `llm-observability.redaction.enabled` (default true) and
`llm-observability.redaction.categories` (comma-separated list). If disabled, all
messages pass through with `RedactionState.NONE`.

If the adapter throws a runtime exception, the service logs the error, leaves the content
unchanged, sets `RedactionState.NONE`, and continues inference. Redaction failure must not
block the stream.

Redaction of assistant completions is deferred.
Retroactive redaction of already-persisted messages is deferred.

## Alternatives Considered

- **NER/ML model-based detection**: Higher recall and fewer false positives on free-form
  text, but requires a model runtime dependency, adds significant latency, and complicates
  local development. Deferred to a future phase as a second adapter behind the same port.
- **Client-side redaction**: Shifts responsibility to callers. Not enforceable server-side.
  Ruled out — the platform must not trust callers to self-redact.
- **No-op redaction (document-only)**: Documents the hook but never exercises it.
  Ruled out — Phase 6 hardening spec requires verified redaction, not documentation only.
- **Encryption-at-rest before persistence**: Orthogonal to redaction. Encryption preserves
  the raw value; redaction removes it. Both can coexist; encryption is deferred.
- **Per-tenant configurable redaction**: More granular but adds configuration API surface
  and complexity. Deferred; global configuration is sufficient for the initial phase.

## Tradeoffs

- Regex patterns have false positives (e.g. phone-like product codes) and false negatives
  (obfuscated or non-standard formats). Accepted for the initial implementation.
- Redacted content breaks the semantic link between the stored message and what the user
  typed. Continuation context will include placeholders instead of originals.
- `input_content_hash` and stored content diverge when redaction fires. Documented.
- Regex compilation at startup is cheap; scanning at request time adds microseconds per
  message. Acceptable at the expected request volume.

## Consequences

- `conversation_message.content` may contain placeholder tokens for redacted messages.
- `conversation_message.redaction_state` transitions from always-`NONE` to
  `REDACTED` when PII is detected.
- `conversation_message.metadata` gains a `redactedCategories` key for redacted messages.
- Provider context for continuation turns uses redacted content.
- `PiiRedactionPort` is infrastructure-free — zero Spring, Kafka, Redis, or R2DBC
  dependency. A future NER adapter requires only a new `PiiRedactionPort` implementation.
- Metrics `pii_redaction_triggered_total` and `pii_redaction_latency_seconds` are
  added to the existing Micrometer registry.
- No Flyway migration required.
- No OpenAPI or Kafka event schema change required.
- Future phases may add: per-tenant policy, assistant completion scanning, retroactive
  redaction jobs, encryption-at-rest, and NER model adapter.
