package com.llmobservability.platform.inferencegateway.application.port.out;

import java.util.List;

/**
 * Result of a PII scan over a single message content string.
 *
 * <p>{@code redactedContent} contains the original text with every detected PII span
 * replaced by its category placeholder (e.g. {@code [EMAIL]}, {@code [PHONE]}).
 * If no PII was detected, {@code redactedContent} equals the original input.
 *
 * <p>{@code detectedCategories} lists the category names that matched
 * (e.g. {@code ["EMAIL", "PHONE"]}). Raw matched values are never included.
 */
public record PiiRedactionResult(
        String redactedContent,
        List<String> detectedCategories
) {
    /**
     * Returns {@code true} when at least one PII category was detected and replaced.
     */
    public boolean hasRedactions() {
        return !detectedCategories.isEmpty();
    }
}
