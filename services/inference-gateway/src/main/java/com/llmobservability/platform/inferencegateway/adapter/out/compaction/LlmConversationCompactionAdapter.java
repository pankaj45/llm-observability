package com.llmobservability.platform.inferencegateway.adapter.out.compaction;

import com.llmobservability.platform.inferencegateway.application.port.in.StartInferenceCommand;
import com.llmobservability.platform.inferencegateway.application.port.out.ConversationCompactionPort;
import com.llmobservability.platform.inferencegateway.application.port.out.ProviderClient;
import com.llmobservability.platform.inferencegateway.application.port.out.ProviderClientRegistry;
import com.llmobservability.platform.inferencegateway.application.service.TokenEstimator;
import com.llmobservability.platform.inferencegateway.domain.model.MessageRole;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
class LlmConversationCompactionAdapter implements ConversationCompactionPort {
    private static final String SYSTEM_PROMPT = """
            You compact conversation history for an AI assistant.

            Produce a concise continuity summary for future turns. Preserve:
            - user goals and preferences
            - explicit decisions and constraints
            - unresolved questions and next steps
            - important facts, entities, identifiers, dates, numbers, errors, and file paths
            - tool/source findings that remain relevant

            Do not include secrets beyond already-redacted placeholders. Do not invent facts.
            Prefer compact bullets. Preserve exact IDs, dates, numbers, commands, and names when present.
            Output only the summary text.
            """;

    private final ProviderClientRegistry providerClientRegistry;

    LlmConversationCompactionAdapter(ProviderClientRegistry providerClientRegistry) {
        this.providerClientRegistry = providerClientRegistry;
    }

    @Override
    public Mono<CompactionResult> compact(CompactionRequest request) {
        return providerClientRegistry.get(request.providerKey())
                .flatMapMany(providerClient -> providerClient.stream(providerRequest(request)))
                .filter(chunk -> chunk.text() != null && !chunk.text().isBlank())
                .map(ProviderClient.ProviderStreamChunk::text)
                .collectList()
                .map(chunks -> String.join("", chunks).strip())
                .filter(summary -> !summary.isBlank())
                .switchIfEmpty(Mono.error(new IllegalStateException("Compaction provider returned an empty summary")))
                .map(summary -> new CompactionResult(summary, TokenEstimator.estimate(summary)));
    }

    private ProviderClient.ProviderRequest providerRequest(CompactionRequest request) {
        List<ProviderClient.ProviderMessage> messages = new ArrayList<>();
        messages.add(new ProviderClient.ProviderMessage(
                MessageRole.SYSTEM.name().toLowerCase(),
                SYSTEM_PROMPT));
        messages.add(new ProviderClient.ProviderMessage(
                MessageRole.USER.name().toLowerCase(),
                userPrompt(request)));
        return new ProviderClient.ProviderRequest(
                request.requestId(),
                request.modelKey(),
                List.copyOf(messages),
                parameters(request.maxSummaryTokens()));
    }

    private Map<String, Object> parameters(int maxSummaryTokens) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("temperature", 0.1);
        parameters.put("maxOutputTokens", maxSummaryTokens);
        return parameters;
    }

    private String userPrompt(CompactionRequest request) {
        StringBuilder prompt = new StringBuilder();
        request.priorSnapshot().ifPresent(snapshot -> prompt
                .append("Existing summary to update:\n")
                .append(snapshot.summaryContent())
                .append("\n\n"));

        prompt.append("Compact these conversation messages into an updated continuity summary.\n")
                .append("Message sequence range: ")
                .append(request.sourceStartSequence())
                .append("-")
                .append(request.sourceEndSequence())
                .append("\n\n");

        for (int i = 0; i < request.messages().size(); i++) {
            StartInferenceCommand.Message message = request.messages().get(i);
            int sequence = request.sourceStartSequence() + i;
            prompt.append("Message ")
                    .append(sequence)
                    .append(" (")
                    .append(message.role().name())
                    .append("):\n")
                    .append(message.content())
                    .append("\n\n");
        }
        return prompt.toString();
    }
}
