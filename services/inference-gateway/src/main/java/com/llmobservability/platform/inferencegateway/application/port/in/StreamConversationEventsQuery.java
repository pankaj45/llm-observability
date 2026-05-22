package com.llmobservability.platform.inferencegateway.application.port.in;

import java.util.UUID;

public record StreamConversationEventsQuery(
        UUID conversationId,
        String tenantId,
        String projectId,
        String after
) {
}
