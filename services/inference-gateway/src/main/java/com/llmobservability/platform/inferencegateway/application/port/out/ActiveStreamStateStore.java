package com.llmobservability.platform.inferencegateway.application.port.out;

import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;

public interface ActiveStreamStateStore {
    Mono<Void> register(UUID requestId, UUID conversationId, Duration ttl);

    Mono<Void> requestCancellation(UUID requestId, Duration ttl);

    Mono<Boolean> cancellationRequested(UUID requestId);

    Mono<Void> clear(UUID requestId);
}

