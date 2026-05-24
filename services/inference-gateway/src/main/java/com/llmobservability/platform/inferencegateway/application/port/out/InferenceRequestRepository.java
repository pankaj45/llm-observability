package com.llmobservability.platform.inferencegateway.application.port.out;

import com.llmobservability.platform.inferencegateway.domain.model.InferenceRequest;
import com.llmobservability.platform.inferencegateway.domain.model.InferenceStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

public interface InferenceRequestRepository {
    Mono<InferenceRequest> save(InferenceRequest request);

    Mono<InferenceRequest> findById(UUID requestId);

    Mono<InferenceRequest> findByIdempotencyKey(String tenantId, String projectId, String idempotencyKey);

    Flux<InferenceRequest> findByConversationId(UUID conversationId);

    Mono<InferenceRequest> findLatestByConversationId(UUID conversationId);

    Mono<InferenceRequest> findActiveByConversationId(UUID conversationId);

    Mono<Void> markStreaming(UUID requestId, Instant firstTokenAt);

    Mono<Boolean> markCompleted(UUID requestId, String outputContentHash, Instant completedAt);

    Mono<Boolean> markCancelled(UUID requestId, Instant cancelledAt);

    Mono<Boolean> markFailed(UUID requestId, Instant failedAt);

    Mono<Void> updateStatus(UUID requestId, InferenceStatus status, Instant updatedAt);
}
