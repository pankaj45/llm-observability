package com.llmobservability.platform.inferencegateway.application.port.in;

import java.util.UUID;

public record CancelConversationStreamCommand(
        UUID conversationId,
        String tenantId,
        String projectId,
        String requestedBy,
        String reason
) {
}
