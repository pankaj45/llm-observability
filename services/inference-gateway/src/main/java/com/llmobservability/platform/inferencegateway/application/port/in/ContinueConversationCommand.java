package com.llmobservability.platform.inferencegateway.application.port.in;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ContinueConversationCommand(
        UUID conversationId,
        String tenantId,
        String projectId,
        String provider,
        String model,
        List<StartInferenceCommand.Message> messages,
        Map<String, Object> parameters,
        Map<String, String> metadata,
        String clientRequestId,
        Map<String, Object> streamOptions,
        String idempotencyKey,
        String traceId
) {
}
