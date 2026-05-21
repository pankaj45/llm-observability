package com.llmobservability.platform.inferencegateway.application.port.in;

import java.util.UUID;

public record GetInferenceStatusQuery(UUID requestId) {
}

