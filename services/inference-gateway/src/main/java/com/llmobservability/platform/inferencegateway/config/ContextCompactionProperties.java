package com.llmobservability.platform.inferencegateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "llm-observability.compaction")
public record ContextCompactionProperties(
        Boolean enabled,
        Double triggerThresholdRatio,
        Double targetThresholdRatio,
        Integer runtimeContextReserveTokens,
        Integer toolEvidenceReserveTokens,
        Integer minExactRecentMessages,
        Integer minMessagesBeyondSnapshot,
        Integer minTokensBeyondSnapshot,
        Integer maxSummaryTokens
) {
    public boolean isEnabled() {
        return enabled == null || enabled;
    }

    public double triggerThresholdRatioValue() {
        return boundedRatio(triggerThresholdRatio, 0.75);
    }

    public double targetThresholdRatioValue() {
        return boundedRatio(targetThresholdRatio, 0.60);
    }

    public int runtimeContextReserveTokensValue() {
        return nonNegative(runtimeContextReserveTokens, 1024);
    }

    public int toolEvidenceReserveTokensValue() {
        return nonNegative(toolEvidenceReserveTokens, 2048);
    }

    public int minExactRecentMessagesValue() {
        return positive(minExactRecentMessages, 12);
    }

    public int minMessagesBeyondSnapshotValue() {
        return positive(minMessagesBeyondSnapshot, 8);
    }

    public int minTokensBeyondSnapshotValue() {
        return positive(minTokensBeyondSnapshot, 2_000);
    }

    public int maxSummaryTokensValue() {
        return positive(maxSummaryTokens, 1_024);
    }

    private static int positive(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    private static int nonNegative(Integer value, int fallback) {
        return value == null || value < 0 ? fallback : value;
    }

    private static double boundedRatio(Double value, double fallback) {
        if (value == null || value <= 0 || value >= 1) {
            return fallback;
        }
        return value;
    }
}
