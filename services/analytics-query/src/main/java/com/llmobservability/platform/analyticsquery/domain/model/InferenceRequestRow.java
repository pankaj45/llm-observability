package com.llmobservability.platform.analyticsquery.domain.model;

import java.time.Instant;
import java.util.UUID;

public record InferenceRequestRow(
        UUID requestId,
        UUID conversationId,
        String provider,
        String model,
        String status,
        Instant startedAt,
        Instant completedAt,
        long durationMs,
        long inputTokens,
        long outputTokens,
        long totalTokens,
        double estimatedCostUsd,
        String errorCode,
        String failureStage,
        String correlationId,
        String traceparent,
        String cursor
) {
    public InferenceRequestRow withCursor(String cursor) {
        return new InferenceRequestRow(
                requestId,
                conversationId,
                provider,
                model,
                status,
                startedAt,
                completedAt,
                durationMs,
                inputTokens,
                outputTokens,
                totalTokens,
                estimatedCostUsd,
                errorCode,
                failureStage,
                correlationId,
                traceparent,
                cursor);
    }

    public Instant completedAtOrStartedAt() {
        return completedAt == null ? startedAt : completedAt;
    }
}
