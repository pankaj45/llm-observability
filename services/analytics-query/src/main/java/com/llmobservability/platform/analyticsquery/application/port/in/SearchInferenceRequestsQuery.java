package com.llmobservability.platform.analyticsquery.application.port.in;

import java.time.Instant;

public record SearchInferenceRequestsQuery(
        String tenantId,
        String projectId,
        Instant from,
        Instant to,
        String provider,
        String model,
        String status,
        String errorCode,
        String cursor,
        int limit
) {
}
