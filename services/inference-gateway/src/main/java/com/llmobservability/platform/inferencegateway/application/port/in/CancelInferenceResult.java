package com.llmobservability.platform.inferencegateway.application.port.in;

import com.llmobservability.platform.inferencegateway.domain.model.InferenceStatus;

import java.util.UUID;

public record CancelInferenceResult(
        UUID requestId,
        UUID conversationId,
        InferenceStatus status,
        boolean providerCancellationAttempted,
        boolean providerCancellationSucceeded
) {
}

