package com.llmobservability.platform.inferencegateway.application.port.in;

import com.llmobservability.platform.inferencegateway.domain.model.MessageRole;
import com.llmobservability.platform.inferencegateway.domain.model.RedactionState;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ConversationMessageResult(
        UUID messageId,
        UUID conversationId,
        MessageRole role,
        int sequence,
        String content,
        String contentHash,
        int estimatedTokens,
        RedactionState redactionState,
        Map<String, String> metadata,
        Instant createdAt,
        String cursor
) {
}
