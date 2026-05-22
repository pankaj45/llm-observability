package com.llmobservability.platform.inferencegateway.application.port.out;

import com.llmobservability.platform.inferencegateway.domain.model.Conversation;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ConversationRepository {
    Mono<Conversation> save(Conversation conversation);

    Mono<Conversation> findById(UUID conversationId);
}

