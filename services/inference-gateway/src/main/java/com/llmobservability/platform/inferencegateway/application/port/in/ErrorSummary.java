package com.llmobservability.platform.inferencegateway.application.port.in;

import com.llmobservability.platform.inferencegateway.domain.model.FailureStage;

public record ErrorSummary(
        FailureStage failureStage,
        String errorCode,
        String providerErrorCode,
        String message,
        boolean retryable
) {
}

