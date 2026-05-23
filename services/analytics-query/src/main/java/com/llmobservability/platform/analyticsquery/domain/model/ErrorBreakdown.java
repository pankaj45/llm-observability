package com.llmobservability.platform.analyticsquery.domain.model;

public record ErrorBreakdown(
        String errorCode,
        String failureStage,
        String providerErrorCode,
        long requestCount,
        long retryableCount
) {
}
