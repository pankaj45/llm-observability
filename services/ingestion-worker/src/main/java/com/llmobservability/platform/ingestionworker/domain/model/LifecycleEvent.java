package com.llmobservability.platform.ingestionworker.domain.model;

import java.time.Instant;
import java.util.Map;

public record LifecycleEvent(
        String eventId,
        String eventName,
        String schemaVersion,
        Instant occurredAt,
        String producer,
        String tenantId,
        String projectId,
        String correlationId,
        String traceparent,
        String idempotencyKey,
        Map<String, Object> payload
) {
    public String dedupeKey() {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            return idempotencyKey;
        }
        return eventId;
    }
}
