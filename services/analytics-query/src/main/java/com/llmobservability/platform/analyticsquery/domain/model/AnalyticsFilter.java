package com.llmobservability.platform.analyticsquery.domain.model;

import java.time.Instant;

public record AnalyticsFilter(
        String tenantId,
        String projectId,
        Instant from,
        Instant to,
        String provider,
        String model,
        String status,
        String errorCode
) {
}
