package com.llmobservability.platform.inferencegateway.application.service.context;

import com.llmobservability.platform.inferencegateway.application.port.in.StartInferenceCommand;

import java.util.List;

public record ContextOrchestrationResult(
        List<StartInferenceCommand.Message> providerMessages,
        List<ContextProgressEvent> progressEvents,
        List<ContextEvidence> evidence
) {
}
