package com.llmobservability.platform.analyticsquery.domain.model;

import java.time.Instant;
import java.util.UUID;

public record InferenceLifecycleEvent(
        UUID eventId,
        String eventName,
        Instant occurredAt,
        String status,
        Long durationMs,
        Integer inputTokens,
        Integer outputTokens,
        Integer totalTokens,
        String errorCode,
        String failureStage,
        String cancellationReason
) {
}
