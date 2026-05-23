package com.llmobservability.platform.inferencegateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Configuration properties for the PII redaction pipeline.
 *
 * <p>Bound from the {@code llm-observability.redaction} prefix.
 * Override via environment variables:
 * <ul>
 *   <li>{@code PII_REDACTION_ENABLED} — set to {@code false} to disable all scanning</li>
 *   <li>{@code PII_REDACTION_CATEGORIES} — comma-separated list of enabled category names</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "llm-observability.redaction")
public class PiiRedactionProperties {

    /**
     * Whether PII redaction is enabled. Defaults to {@code true}.
     * When {@code false}, all messages are stored as-is with {@code RedactionState.NONE}.
     */
    private boolean enabled = true;

    /**
     * PII category names to scan for. Each name must match a category implemented
     * by the active {@link com.llmobservability.platform.inferencegateway.application.port.out.PiiRedactionPort}
     * adapter. Unknown names are silently ignored.
     *
     * <p>Defaults to the full initial set: EMAIL, PHONE, CREDIT_CARD, SSN, IP_ADDRESS, AADHAAR.
     */
    private List<String> categories = List.of(
            "EMAIL", "PHONE", "CREDIT_CARD", "SSN", "IP_ADDRESS", "AADHAAR"
    );

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getCategories() {
        return categories;
    }

    public void setCategories(List<String> categories) {
        this.categories = categories;
    }
}
