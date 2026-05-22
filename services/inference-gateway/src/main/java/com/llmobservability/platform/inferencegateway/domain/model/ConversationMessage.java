package com.llmobservability.platform.inferencegateway.domain.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ConversationMessage(
        UUID id,
        UUID conversationId,
        MessageRole role,
        int sequence,
        String content,
        String contentHash,
        int estimatedTokens,
        RedactionState redactionState,
        Map<String, String> metadata,
        Instant createdAt
) {
}
