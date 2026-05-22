package com.llmobservability.platform.inferencegateway.application.port.in;

import java.util.UUID;

public record GetConversationQuery(
        UUID conversationId,
        String tenantId,
        String projectId
) {
}
