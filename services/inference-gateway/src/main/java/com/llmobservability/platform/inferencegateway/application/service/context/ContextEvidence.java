package com.llmobservability.platform.inferencegateway.application.service.context;

import java.time.Duration;
import java.time.Instant;

public record ContextEvidence(
        String evidenceId,
        String toolName,
        String sourceName,
        String sourceUrl,
        String title,
        Instant fetchedAt,
        Instant publishedAt,
        String content,
        String confidence,
        Duration freshnessWindow
) {
}
