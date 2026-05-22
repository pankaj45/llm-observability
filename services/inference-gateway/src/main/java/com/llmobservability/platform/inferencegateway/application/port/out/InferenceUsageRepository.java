package com.llmobservability.platform.inferencegateway.application.port.out;

import com.llmobservability.platform.inferencegateway.domain.model.InferenceUsage;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface InferenceUsageRepository {
    Mono<Void> save(InferenceUsage usage);

    Mono<InferenceUsage> findByRequestId(UUID requestId);
}

