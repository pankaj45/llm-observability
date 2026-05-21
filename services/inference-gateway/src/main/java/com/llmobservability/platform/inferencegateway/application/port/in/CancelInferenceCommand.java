package com.llmobservability.platform.inferencegateway.application.port.in;

import java.util.UUID;

public record CancelInferenceCommand(
        UUID requestId,
        String requestedBy,
        String reason
) {
}

