package com.llmobservability.platform.inferencegateway.application.port.out;

import com.llmobservability.platform.inferencegateway.domain.model.ConversationMessage;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

public interface ConversationMessageRepository {
    Mono<Void> saveAll(List<ConversationMessage> messages);

    Mono<Void> save(ConversationMessage message);

    Flux<ConversationMessage> findByConversationId(UUID conversationId);

    Flux<ConversationMessage> findByConversationIdAfterSequence(UUID conversationId, int afterSequence, int limit);

    Mono<Long> countByConversationId(UUID conversationId);

    Mono<ConversationMessage> findLatestByConversationId(UUID conversationId);
}
