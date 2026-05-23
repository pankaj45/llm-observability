package com.llmobservability.platform.analyticsquery.domain.model;

import java.time.Instant;

public record InferenceTimeSeriesPoint(
        Instant bucket,
        long requestCount,
        long successCount,
        long failedCount,
        long cancelledCount,
        double averageDurationMs,
        double p95DurationMs,
        long totalTokens
) {
}
