package com.llmobservability.platform.inferencegateway.application.port.out;

import com.llmobservability.platform.inferencegateway.domain.model.StreamEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;

public interface ActiveStreamStateStore {
    Mono<Void> register(UUID requestId, UUID conversationId, Duration ttl);

    Mono<Void> appendEvent(UUID requestId, UUID conversationId, StreamEvent event, Duration ttl);

    Flux<StreamEvent> replayEvents(UUID conversationId, String afterEventId);

    Mono<UUID> findActiveRequestId(UUID conversationId);

    Mono<Void> requestCancellation(UUID requestId, Duration ttl);

    Mono<Boolean> cancellationRequested(UUID requestId);

    Mono<Void> clear(UUID requestId);
}
