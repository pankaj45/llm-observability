package com.llmobservability.platform.inferencegateway.application.port.out;

import com.llmobservability.platform.inferencegateway.domain.model.ConversationContextSnapshot;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ConversationContextSnapshotRepository {
    Mono<Void> save(ConversationContextSnapshot snapshot);

    Mono<ConversationContextSnapshot> findLatest(UUID conversationId, String providerKey, String modelKey);
}
