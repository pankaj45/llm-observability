package com.llmobservability.platform.analyticsquery.domain.model;

public record DimensionBreakdown(
        String name,
        long requestCount,
        double percentage,
        double averageDurationMs,
        long totalTokens,
        double estimatedCostUsd
) {
}
