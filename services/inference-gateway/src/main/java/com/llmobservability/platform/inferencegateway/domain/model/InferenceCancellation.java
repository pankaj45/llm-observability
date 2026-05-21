package com.llmobservability.platform.inferencegateway.domain.model;

import java.time.Instant;
import java.util.UUID;

public record InferenceCancellation(
        UUID id,
        UUID inferenceRequestId,
        String requestedBy,
        String reason,
        boolean providerCancellationAttempted,
        boolean providerCancellationSucceeded,
        Instant createdAt,
        Instant resolvedAt
) {
}

