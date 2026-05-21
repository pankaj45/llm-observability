package com.llmobservability.platform.inferencegateway.application.port.in;

import com.llmobservability.platform.inferencegateway.domain.model.MessageRole;

import java.util.List;
import java.util.Map;

public record StartInferenceCommand(
        String tenantId,
        String projectId,
        String provider,
        String model,
        List<Message> messages,
        Map<String, Object> parameters,
        Map<String, String> metadata,
        String clientRequestId,
        Map<String, Object> streamOptions,
        String idempotencyKey,
        String traceId
) {
    public record Message(MessageRole role, String content) {
    }
}

