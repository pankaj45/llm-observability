package com.llmobservability.platform.inferencegateway.application.service;

import com.llmobservability.platform.inferencegateway.application.port.in.StartInferenceCommand;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public record ConversationContextAssembly(
        List<StartInferenceCommand.Message> providerMessages,
        boolean compacted,
        Optional<UUID> snapshotId,
        int inputTokensBefore,
        int inputTokensAfter
) {
    static ConversationContextAssembly exact(List<StartInferenceCommand.Message> providerMessages, int estimatedTokens) {
        return new ConversationContextAssembly(providerMessages, false, Optional.empty(), estimatedTokens, estimatedTokens);
    }
}
