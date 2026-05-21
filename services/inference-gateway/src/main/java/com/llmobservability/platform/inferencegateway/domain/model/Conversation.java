package com.llmobservability.platform.inferencegateway.domain.model;

import java.time.Instant;
import java.util.UUID;

public record Conversation(
        UUID id,
        String tenantId,
        String projectId,
        ConversationStatus status,
        String title,
        String titleSource,
        Instant createdAt,
        Instant updatedAt,
        Instant cancelledAt
) {
}

