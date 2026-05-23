package com.llmobservability.platform.analyticsquery.domain.model;

public record InferenceTotals(
        long requestCount,
        long successCount,
        long failedCount,
        long cancelledCount,
        long inputTokens,
        long outputTokens,
        long totalTokens,
        double averageDurationMs,
        double p95DurationMs,
        double errorRate,
        double estimatedCostUsd
) {
    public static InferenceTotals empty() {
        return new InferenceTotals(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }
}
