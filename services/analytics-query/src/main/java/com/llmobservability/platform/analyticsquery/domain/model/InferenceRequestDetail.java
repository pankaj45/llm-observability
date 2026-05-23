package com.llmobservability.platform.analyticsquery.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InferenceRequestDetail(
        UUID requestId,
        UUID conversationId,
        String tenantId,
        String projectId,
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
        String providerErrorCode,
        Boolean retryable,
        String cancellationReason,
        Boolean providerCancellationAttempted,
        Boolean providerCancellationSucceeded,
        String correlationId,
        String traceparent,
        List<InferenceLifecycleEvent> events
) {
}
