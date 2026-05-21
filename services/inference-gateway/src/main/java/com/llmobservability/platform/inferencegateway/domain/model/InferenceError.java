package com.llmobservability.platform.inferencegateway.domain.model;

import java.time.Instant;
import java.util.UUID;

public record InferenceError(
        UUID id,
        UUID inferenceRequestId,
        FailureStage failureStage,
        String errorCode,
        String providerErrorCode,
        String message,
        boolean retryable,
        Instant createdAt
) {
}

