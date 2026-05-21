package com.llmobservability.platform.inferencegateway.adapter.in.web;

import com.llmobservability.platform.inferencegateway.application.port.in.ErrorSummary;
import com.llmobservability.platform.inferencegateway.application.port.in.InferenceStatusResult;
import com.llmobservability.platform.inferencegateway.application.port.in.UsageSummary;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record InferenceStatusResponse(
        UUID requestId,
        UUID conversationId,
        String tenantId,
        String projectId,
        String provider,
        String model,
        String status,
        Instant createdAt,
        Instant startedAt,
        Instant firstTokenAt,
        Instant completedAt,
        Instant cancelledAt,
        Instant failedAt,
        UsageResponse usage,
        ErrorResponse error,
        Map<String, String> metadata
) {
    static InferenceStatusResponse from(InferenceStatusResult result) {
        return new InferenceStatusResponse(
                result.requestId(),
                result.conversationId(),
                result.tenantId(),
                result.projectId(),
                result.provider(),
                result.model(),
                result.status().name(),
                result.createdAt(),
                result.startedAt(),
                result.firstTokenAt(),
                result.completedAt(),
                result.cancelledAt(),
                result.failedAt(),
                UsageResponse.from(result.usage()),
                ErrorResponse.from(result.error()),
                result.metadata());
    }

    record UsageResponse(
            int inputTokens,
            int outputTokens,
            int totalTokens,
            BigDecimal estimatedCostAmount,
            String estimatedCostCurrency
    ) {
        static UsageResponse from(UsageSummary usage) {
            if (usage == null) {
                return null;
            }
            return new UsageResponse(
                    usage.inputTokens(),
                    usage.outputTokens(),
                    usage.totalTokens(),
                    usage.estimatedCostAmount(),
                    usage.estimatedCostCurrency());
        }
    }

    record ErrorResponse(
            String failureStage,
            String errorCode,
            String providerErrorCode,
            String message,
            boolean retryable
    ) {
        static ErrorResponse from(ErrorSummary error) {
            if (error == null) {
                return null;
            }
            return new ErrorResponse(
                    error.failureStage().name(),
                    error.errorCode(),
                    error.providerErrorCode(),
                    error.message(),
                    error.retryable());
        }
    }
}
