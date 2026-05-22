package com.llmobservability.platform.inferencegateway.application.port.out;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface ProviderClient {
    String providerKey();

    Flux<ProviderStreamChunk> stream(ProviderRequest request);

    Mono<ProviderCancellationResult> cancel(UUID requestId);

    record ProviderRequest(
            UUID requestId,
            String model,
            List<ProviderMessage> messages,
            Map<String, Object> parameters
    ) {
    }

    record ProviderMessage(String role, String content) {
    }

    record ProviderStreamChunk(
            String text,
            Integer inputTokens,
            Integer outputTokens,
            String finishReason,
            String providerEventType
    ) {
    }

    record ProviderCancellationResult(boolean attempted, boolean succeeded) {
    }
}

