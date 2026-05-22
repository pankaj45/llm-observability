package com.llmobservability.platform.inferencegateway.application.port.out;

import com.llmobservability.platform.inferencegateway.domain.model.InferenceCancellation;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface InferenceCancellationRepository {
    Mono<Void> save(InferenceCancellation cancellation);

    Mono<InferenceCancellation> findLatest(UUID requestId);
}

