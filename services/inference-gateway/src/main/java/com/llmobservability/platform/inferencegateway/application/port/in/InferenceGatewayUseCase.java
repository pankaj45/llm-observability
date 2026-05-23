package com.llmobservability.platform.inferencegateway.application.port.in;

import com.llmobservability.platform.inferencegateway.domain.model.StreamEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface InferenceGatewayUseCase {
    Flux<StreamEvent> stream(StartInferenceCommand command);

    Flux<StreamEvent> continueConversation(ContinueConversationCommand command);

    Mono<PagedResult<ConversationMetadataResult>> listConversations(ListConversationsQuery query);

    Mono<ConversationMetadataResult> getConversation(GetConversationQuery query);

    Mono<PagedResult<ConversationMessageResult>> listConversationMessages(ListConversationMessagesQuery query);

    Mono<PagedResult<ConversationTimelineEventResult>> listConversationEvents(ListConversationEventsQuery query);

    Flux<StreamEvent> streamConversationEvents(StreamConversationEventsQuery query);

    Mono<CancelInferenceResult> cancel(CancelInferenceCommand command);

    Mono<CancelInferenceResult> cancelConversationStream(CancelConversationStreamCommand command);

    Mono<InferenceStatusResult> status(GetInferenceStatusQuery query);

    /** Returns all enabled models grouped by provider, for use by the chatbot UI model selector. */
    Flux<ModelCatalogResult> listModels();
}
