package com.llmobservability.platform.inferencegateway.application.port.out;

/**
 * Outbound port for PII detection and redaction.
 *
 * <p>Implementations must be stateless, synchronous, and free of Spring, Kafka,
 * Redis, R2DBC, or WebClient dependencies. The domain calls this port before
 * persisting any {@code ConversationMessage}.
 */
public interface PiiRedactionPort {

    /**
     * Scans {@code content} for PII and returns a {@link PiiRedactionResult}
     * with the redacted text and the list of detected category names.
     *
     * <p>Implementations must never include raw matched values in the result.
     * If an exception occurs, callers should log the error, treat the content
     * as unredacted ({@code RedactionState.NONE}), and continue inference.
     *
     * @param content the raw message content to scan; must not be {@code null}
     * @return a non-null result; never throws checked exceptions
     */
    PiiRedactionResult scan(String content);
}
