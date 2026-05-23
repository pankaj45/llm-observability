# Phase 09 Specification: PII Redaction

## Implementation Status

Status as of 2026-05-24: draft, pending review and approval.

## Problem Statement

`conversation_message` rows persist raw user prompt and model completion content in PostgreSQL.
This content may contain personally identifiable information (PII) such as names, email
addresses, phone numbers, credit card numbers, national ID numbers, and IP addresses.

The platform already:
- models `RedactionState` on `conversation_message` (always `NONE` today)
- excludes raw content from Kafka events, ClickHouse, logs, and metrics
- enforces tenant/project scoping on all message read APIs

But no scanning, detection, or redaction of sensitive content occurs before or after persistence.
Any tenant submitting PII in a chat prompt stores it unmodified, with no audit trail, no
redaction record, and no policy controls.

## Goals

- Scan every user message before persistence for a configurable set of PII categories.
- Replace detected PII spans with redaction placeholders (e.g. `[EMAIL]`, `[PHONE]`).
- Persist the redacted text; never persist detected raw PII after redaction is triggered.
- Record what categories were detected (not the raw values) in `redaction_state` and `metadata`.
- Keep the redaction decision and execution inside the domain, behind a port.
- Allow redaction to be toggled per tenant/project through configuration.
- Emit a structured audit event when redaction fires.
- Not block inference: redaction happens before persistence but must not fail the stream.
- Not send raw PII to the provider: redacted text is used in provider context assembly.

## Non-Goals

- No ML/NER model-based redaction in the initial implementation; regex patterns only.
- No redaction of assistant (model) completions in the initial implementation.
- No retroactive redaction of already-persisted messages.
- No encryption-at-rest of message content beyond existing PostgreSQL controls.
- No per-field granular encryption.
- No client-visible redaction indicators in SSE stream events.
- No GDPR right-to-erasure workflow (future phase).
- No redaction of tool evidence or web search results.

## Proposed Architecture

```
User message (StartInferenceCommand / ContinueConversationCommand)
    │
    ▼
PiiRedactionPort.scan(content) ──► PiiRedactionResult(redactedContent, detectedCategories)
    │
    ├── detectedCategories empty ──► RedactionState.NONE, original content persisted
    │
    └── detectedCategories non-empty ──► RedactionState.REDACTED, redacted content persisted
                                          metadata records detected categories (not raw values)
                                          audit event emitted
```

The redaction port lives in the **application layer** (`application/port/out`). The regex
implementation lives in `adapter/out/redaction`. The `InferenceGatewayService` calls the
port for each user message before building `ConversationMessage` records. Provider context
assembly uses the already-redacted content.

## Proposed Components

### PII Categories (Initial Set)

| Category | Placeholder | Pattern Notes |
|---|---|---|
| `EMAIL` | `[EMAIL]` | RFC 5321 local@domain form |
| `PHONE` | `[PHONE]` | E.164 and common local formats |
| `CREDIT_CARD` | `[CREDIT_CARD]` | 13–19 digit Luhn-verifiable sequences |
| `SSN` | `[SSN]` | US Social Security Number `NNN-NN-NNNN` |
| `IP_ADDRESS` | `[IP_ADDRESS]` | IPv4 dotted-quad |
| `AADHAAR` | `[AADHAAR]` | 12-digit Indian national ID |

Additional categories (future): `PASSPORT`, `BANK_ACCOUNT`, `DATE_OF_BIRTH`, `FULL_NAME` (NER).

### `PiiRedactionPort` (outbound port)

```java
public interface PiiRedactionPort {
    PiiRedactionResult scan(String content);
}

public record PiiRedactionResult(
    String redactedContent,        // content with PII replaced by placeholders
    List<String> detectedCategories // e.g. ["EMAIL", "PHONE"] — no raw values
) {
    public boolean hasRedactions() { return !detectedCategories.isEmpty(); }
}
```

### `RegexPiiRedactionAdapter` (adapter)

- Applies each category's compiled `Pattern` in order.
- Replaces all non-overlapping matches with the category placeholder.
- Returns `PiiRedactionResult` with the final redacted string and the set of triggered categories.
- Enabled/disabled via `PiiRedactionProperties`.

### `RedactionState` extension

Add a new value to indicate partial redaction in the future. For now `REDACTED` covers the case.
`metadata` records `redactedCategories` as a JSON array: `["EMAIL", "PHONE"]`.

### `PiiRedactionProperties`

```yaml
llm-observability:
  redaction:
    enabled: true
    categories:
      - EMAIL
      - PHONE
      - CREDIT_CARD
      - SSN
      - IP_ADDRESS
      - AADHAAR
```

Configurable via environment variables `PII_REDACTION_ENABLED` and
`PII_REDACTION_CATEGORIES` (comma-separated list).

### Integration Point in `InferenceGatewayService`

For each user `StartInferenceCommand.Message` before `ConversationMessage` construction:

1. Call `piiRedactionPort.scan(message.content())`.
2. If `hasRedactions()`:
   - use `result.redactedContent()` as persisted content.
   - set `redactionState = RedactionState.REDACTED`.
   - add `redactedCategories` to message metadata.
   - emit a redaction audit log line (structured, no raw content).
3. Use the (possibly redacted) content for all downstream steps:
   - `ConversationMessage` persistence
   - provider context assembly
   - conversation history sent to the provider on continuation

### Flyway Migration

No schema change required. `conversation_message.redaction_state` and
`conversation_message.metadata` already exist and accept `REDACTED` and JSON respectively.

### Audit Event

Emit a structured log line (not a Kafka event for Phase 09) at INFO level:

```
audit.pii.redacted conversationId=<uuid> messageSequence=<n> categories=["EMAIL"]
```

No raw content, no matched values in the log line.

## Privacy and Security Requirements

- Never log the raw PII value or the matched substring.
- Never publish `detectedCategories` to Kafka events.
- Never return `detectedCategories` in any API response.
- The redacted content (with placeholders) is used for all downstream operations including
  provider context, so the provider never receives raw PII either.
- If the redaction adapter throws, log the error, leave content unchanged, and set
  `redactionState = NONE` — do not block inference.

## Observability Requirements

Metrics:
- `pii_redaction_triggered_total` — counter by category (label `category`)
- `pii_redaction_latency_seconds` — histogram for scan time

Logging:
- Structured audit line per redacted message (no raw values)

## Acceptance Criteria

- Every user message is scanned before persistence when redaction is enabled.
- Detected PII is replaced with the correct placeholder in the persisted content.
- `redaction_state = 'REDACTED'` and `metadata` contains `redactedCategories` for redacted messages.
- Provider receives the redacted content, not the original.
- No raw PII value appears in logs, Kafka events, metrics, or traces.
- Redaction failure does not block the inference stream.
- `PiiRedactionPort` has zero dependency on Spring, WebClient, Kafka, Redis, or R2DBC.
- Unit tests cover all six initial PII categories, multi-category detection, no-match path, and failure fallback.
- `mvn -pl services/inference-gateway test` passes.
- `npm run contracts` passes.

## Risks

- Regex false positives can corrupt valid content (e.g. phone-like product codes).
- Regex false negatives miss obfuscated or non-standard PII formats.
- Redacting content changes the hash used for idempotency checks; `input_content_hash` will
  reflect the pre-redaction content (from the command), while the persisted message stores
  the redacted content. This inconsistency must be documented.
- Continuation context assembly uses persisted (redacted) content, so the provider context
  will include placeholders rather than original values on subsequent turns.

## Open Questions

1. Should `input_content_hash` on `inference_request` be computed from pre-redaction or
   post-redaction content? Current approach: pre-redaction (preserves idempotency key
   semantics; the hash identifies the request, not the stored text).
2. Should assistant completions be scanned? Deferred — model output redaction is more
   complex and has different latency implications.
3. Should `redactedCategories` be surfaced to tenant admins through a future audit API?
   Deferred to a later phase.
4. Should redaction be a per-tenant/project policy (not just global)? Deferred; initial
   implementation is per-deployment via configuration.
