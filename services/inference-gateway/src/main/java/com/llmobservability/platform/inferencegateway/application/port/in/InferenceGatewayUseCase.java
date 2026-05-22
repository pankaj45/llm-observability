package com.llmobservability.platform.inferencegateway.application.port.in;

import com.llmobservability.platform.inferencegateway.domain.model.StreamEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface InferenceGatewayUseCase {
    Flux<StreamEvent> stream(StartInferenceCommand command);

    Flux<StreamEvent> continueConversation(ContinueConversationCommand command);

    Mono<CancelInferenceResult> cancel(CancelInferenceCommand command);

    Mono<InferenceStatusResult> status(GetInferenceStatusQuery query);
}
