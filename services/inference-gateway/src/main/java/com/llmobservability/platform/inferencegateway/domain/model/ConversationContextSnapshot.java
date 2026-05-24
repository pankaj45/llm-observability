package com.llmobservability.platform.inferencegateway.domain.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ConversationContextSnapshot(
        UUID id,
        UUID conversationId,
        int sourceStartSequence,
        int sourceEndSequence,
        String summaryContent,
        String summaryContentHash,
        int estimatedTokens,
        String compactionStrategy,
        String providerKey,
        String modelKey,
        UUID createdByRequestId,
        Map<String, String> metadata,
        Instant createdAt
) {
}
