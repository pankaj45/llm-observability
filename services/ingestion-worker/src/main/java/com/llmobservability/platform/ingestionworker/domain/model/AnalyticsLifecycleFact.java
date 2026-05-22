package com.llmobservability.platform.ingestionworker.domain.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record AnalyticsLifecycleFact(
        @JsonProperty("event_id")
        String eventId,
        @JsonProperty("event_name")
        String eventName,
        @JsonProperty("schema_version")
        String schemaVersion,
        @JsonProperty("occurred_at")
        Instant occurredAt,
        String producer,
        @JsonProperty("tenant_id")
        String tenantId,
        @JsonProperty("project_id")
        String projectId,
        @JsonProperty("correlation_id")
        String correlationId,
        String traceparent,
        @JsonProperty("request_id")
        String requestId,
        @JsonProperty("conversation_id")
        String conversationId,
        String provider,
        String model,
        String status,
        @JsonProperty("input_message_count")
        Integer inputMessageCount,
        @JsonProperty("input_content_hash")
        String inputContentHash,
        @JsonProperty("input_tokens")
        Integer inputTokens,
        @JsonProperty("output_tokens")
        Integer outputTokens,
        @JsonProperty("total_tokens")
        Integer totalTokens,
        @JsonProperty("duration_ms")
        Long durationMs,
        @JsonProperty("failure_stage")
        String failureStage,
        @JsonProperty("error_code")
        String errorCode,
        @JsonProperty("provider_error_code")
        String providerErrorCode,
        Boolean retryable,
        @JsonProperty("cancellation_reason")
        String cancellationReason,
        @JsonProperty("provider_cancellation_attempted")
        Boolean providerCancellationAttempted,
        @JsonProperty("provider_cancellation_succeeded")
        Boolean providerCancellationSucceeded
) {
}
