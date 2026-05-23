package com.llmobservability.platform.inferencegateway.application.service.context;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ContextToolInvocation(
        UUID id,
        String tenantId,
        String projectId,
        UUID conversationId,
        UUID inferenceRequestId,
        String toolName,
        String toolProvider,
        String inputHash,
        ToolInvocationStatus status,
        Instant startedAt,
        Instant completedAt,
        long latencyMs,
        boolean cacheHit,
        int resultCount,
        List<String> sourceUrls,
        String errorCode,
        Map<String, String> metadata
) {
}
