package com.llmobservability.platform.inferencegateway.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record InferenceUsage(
        UUID id,
        UUID inferenceRequestId,
        int inputTokens,
        int outputTokens,
        int totalTokens,
        String providerReportedUnits,
        BigDecimal estimatedCostAmount,
        String estimatedCostCurrency,
        Instant createdAt
) {
}

