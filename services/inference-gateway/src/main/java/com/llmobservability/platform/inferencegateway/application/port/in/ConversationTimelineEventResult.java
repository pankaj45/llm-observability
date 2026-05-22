package com.llmobservability.platform.inferencegateway.application.port.in;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ConversationTimelineEventResult(
        String id,
        String type,
        UUID conversationId,
        UUID requestId,
        UUID messageId,
        long sequence,
        Instant occurredAt,
        Map<String, Object> data,
        String cursor
) {
}
