package com.llmobservability.platform.inferencegateway.application.port.in;

import com.llmobservability.platform.inferencegateway.domain.model.ConversationStatus;
import com.llmobservability.platform.inferencegateway.domain.model.InferenceStatus;

import java.time.Instant;
import java.util.UUID;

public record ConversationMetadataResult(
        UUID conversationId,
        String tenantId,
        String projectId,
        ConversationStatus status,
        String title,
        String titleSource,
        Instant createdAt,
        Instant updatedAt,
        Instant cancelledAt,
        Instant lastMessageAt,
        long messageCount,
        UUID activeRequestId,
        InferenceStatus latestRequestStatus
) {
}
