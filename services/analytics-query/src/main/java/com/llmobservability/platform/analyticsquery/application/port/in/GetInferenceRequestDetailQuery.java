package com.llmobservability.platform.analyticsquery.application.port.in;

import java.time.Instant;
import java.util.UUID;

public record GetInferenceRequestDetailQuery(
        UUID requestId,
        String tenantId,
        String projectId,
        Instant from,
        Instant to
) {
}
