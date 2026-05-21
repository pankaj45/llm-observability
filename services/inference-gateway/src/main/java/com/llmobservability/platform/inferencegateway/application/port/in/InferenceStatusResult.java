package com.llmobservability.platform.inferencegateway.application.port.in;

import com.llmobservability.platform.inferencegateway.domain.model.InferenceStatus;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record InferenceStatusResult(
        UUID requestId,
        UUID conversationId,
        String tenantId,
        String projectId,
        String provider,
        String model,
        InferenceStatus status,
        Instant createdAt,
        Instant startedAt,
        Instant firstTokenAt,
        Instant completedAt,
        Instant cancelledAt,
        Instant failedAt,
        UsageSummary usage,
        ErrorSummary error,
        Map<String, String> metadata
) {
}

