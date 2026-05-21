package com.llmobservability.platform.inferencegateway.application.port.in;

import java.math.BigDecimal;

public record UsageSummary(
        int inputTokens,
        int outputTokens,
        int totalTokens,
        BigDecimal estimatedCostAmount,
        String estimatedCostCurrency
) {
}

