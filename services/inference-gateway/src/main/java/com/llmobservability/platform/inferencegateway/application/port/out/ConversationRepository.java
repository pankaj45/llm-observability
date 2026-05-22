package com.llmobservability.platform.inferencegateway.application.port.out;

import com.llmobservability.platform.inferencegateway.domain.model.Conversation;
import com.llmobservability.platform.inferencegateway.domain.model.ConversationStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

public interface ConversationRepository {
    Mono<Conversation> save(Conversation conversation);

    Mono<Conversation> findById(UUID conversationId);

    Flux<Conversation> findByTenantProject(String tenantId, String projectId, ConversationStatus status, Instant beforeUpdatedAt, int limit);
}
