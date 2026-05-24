package com.llmobservability.platform.inferencegateway.application.port.out;

import com.llmobservability.platform.inferencegateway.application.port.in.StartInferenceCommand;
import com.llmobservability.platform.inferencegateway.domain.model.ConversationContextSnapshot;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationCompactionPort {
    Mono<CompactionResult> compact(CompactionRequest request);

    record CompactionRequest(
            UUID conversationId,
            UUID requestId,
            String providerKey,
            String modelKey,
            int sourceStartSequence,
            int sourceEndSequence,
            Optional<ConversationContextSnapshot> priorSnapshot,
            List<StartInferenceCommand.Message> messages,
            int maxSummaryTokens
    ) {
    }

    record CompactionResult(String summaryContent, int estimatedTokens) {
    }
}
