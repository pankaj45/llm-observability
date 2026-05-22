package com.llmobservability.platform.inferencegateway.application.port.in;

import java.util.UUID;

public record ListConversationMessagesQuery(
        UUID conversationId,
        String tenantId,
        String projectId,
        String after,
        int limit
) {
}
