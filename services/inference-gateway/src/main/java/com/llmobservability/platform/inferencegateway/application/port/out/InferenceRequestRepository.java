package com.llmobservability.platform.inferencegateway.application.port.out;

import com.llmobservability.platform.inferencegateway.domain.model.InferenceRequest;
import com.llmobservability.platform.inferencegateway.domain.model.InferenceStatus;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

public interface InferenceRequestRepository {
    Mono<InferenceRequest> save(InferenceRequest request);

    Mono<InferenceRequest> findById(UUID requestId);

    Mono<InferenceRequest> findByIdempotencyKey(String tenantId, String projectId, String idempotencyKey);

    Mono<Void> markStreaming(UUID requestId, Instant firstTokenAt);

    Mono<Void> markCompleted(UUID requestId, String outputContentHash, Instant completedAt);

    Mono<Void> markCancelled(UUID requestId, Instant cancelledAt);

    Mono<Void> markFailed(UUID requestId, Instant failedAt);

    Mono<Void> updateStatus(UUID requestId, InferenceStatus status, Instant updatedAt);
}

