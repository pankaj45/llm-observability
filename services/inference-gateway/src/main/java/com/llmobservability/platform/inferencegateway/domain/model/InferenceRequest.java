package com.llmobservability.platform.inferencegateway.domain.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record InferenceRequest(
        UUID id,
        String tenantId,
        String projectId,
        UUID conversationId,
        UUID providerId,
        UUID modelId,
        String providerKey,
        String modelKey,
        String idempotencyKey,
        InferenceStatus status,
        boolean streaming,
        Map<String, String> requestMetadata,
        int inputMessageCount,
        String inputContentHash,
        String outputContentHash,
        String redisStreamKey,
        Instant createdAt,
        Instant startedAt,
        Instant firstTokenAt,
        Instant completedAt,
        Instant cancelledAt,
        Instant failedAt,
        Instant updatedAt
) {
    public boolean active() {
        return status == InferenceStatus.ACCEPTED || status == InferenceStatus.STREAMING;
    }
}

