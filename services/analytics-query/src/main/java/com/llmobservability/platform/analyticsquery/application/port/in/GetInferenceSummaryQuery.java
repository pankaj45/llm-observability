package com.llmobservability.platform.analyticsquery.application.port.in;

import java.time.Instant;

public record GetInferenceSummaryQuery(
        String tenantId,
        String projectId,
        Instant from,
        Instant to,
        String provider,
        String model,
        String status
) {
}
