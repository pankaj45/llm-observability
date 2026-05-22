package com.llmobservability.platform.inferencegateway.application.port.in;

import com.llmobservability.platform.inferencegateway.domain.model.ConversationStatus;

public record ListConversationsQuery(
        String tenantId,
        String projectId,
        ConversationStatus status,
        String cursor,
        int limit
) {
}
