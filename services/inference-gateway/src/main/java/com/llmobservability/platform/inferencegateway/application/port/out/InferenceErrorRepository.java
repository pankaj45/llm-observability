package com.llmobservability.platform.inferencegateway.application.port.out;

import com.llmobservability.platform.inferencegateway.domain.model.InferenceError;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface InferenceErrorRepository {
    Mono<Void> save(InferenceError error);

    Mono<InferenceError> findLatest(UUID requestId);
}

