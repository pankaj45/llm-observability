package com.llmobservability.platform.inferencegateway.domain.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record StreamEvent(
        String id,
        StreamEventType type,
        UUID requestId,
        UUID conversationId,
        String traceId,
        long sequence,
        Instant occurredAt,
        Map<String, Object> data
) {
}

