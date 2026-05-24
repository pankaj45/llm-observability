package com.llmobservability.platform.inferencegateway.adapter.out.compaction;

import com.llmobservability.platform.inferencegateway.application.port.in.StartInferenceCommand;
import com.llmobservability.platform.inferencegateway.application.port.out.ConversationCompactionPort;
import com.llmobservability.platform.inferencegateway.application.port.out.ProviderClient;
import com.llmobservability.platform.inferencegateway.application.port.out.ProviderClientRegistry;
import com.llmobservability.platform.inferencegateway.domain.model.MessageRole;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LlmConversationCompactionAdapterTest {

    @Test
    void compactsByCallingConfiguredProviderWithSummaryPrompt() {
        List<ProviderClient.ProviderRequest> providerRequests = new ArrayList<>();
        ProviderClient providerClient = new ProviderClient() {
            @Override
            public String providerKey() {
                return "gemini";
            }

            @Override
            public Flux<ProviderStreamChunk> stream(ProviderRequest request) {
                providerRequests.add(request);
                return Flux.just(
                        new ProviderStreamChunk("summary ", null, null, null, "test"),
                        new ProviderStreamChunk("text", 10, 2, "STOP", "test"));
            }

            @Override
            public Mono<ProviderCancellationResult> cancel(UUID requestId) {
                return Mono.just(new ProviderCancellationResult(false, false));
            }
        };
        ProviderClientRegistry registry = providerKey -> Mono.just(providerClient);
        LlmConversationCompactionAdapter adapter = new LlmConversationCompactionAdapter(registry);

        StepVerifier.create(adapter.compact(new ConversationCompactionPort.CompactionRequest(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "gemini",
                        "gemini-1.5-flash",
                        0,
                        1,
                        Optional.empty(),
                        List.of(
                                new StartInferenceCommand.Message(MessageRole.USER, "remember the account id acct-123"),
                                new StartInferenceCommand.Message(MessageRole.ASSISTANT, "confirmed")),
                        128)))
                .assertNext(result -> {
                    assertThat(result.summaryContent()).isEqualTo("summary text");
                    assertThat(result.estimatedTokens()).isPositive();
                })
                .verifyComplete();

        assertThat(providerRequests).singleElement()
                .satisfies(request -> {
                    assertThat(request.model()).isEqualTo("gemini-1.5-flash");
                    assertThat(request.parameters()).containsEntry("maxOutputTokens", 128);
                    assertThat(request.messages()).hasSize(2);
                    assertThat(request.messages().getFirst().role()).isEqualTo("system");
                    assertThat(request.messages().getLast().content()).contains("acct-123");
                });
    }

    @Test
    void emptyProviderSummaryFailsCompaction() {
        ProviderClient providerClient = new ProviderClient() {
            @Override
            public String providerKey() {
                return "gemini";
            }

            @Override
            public Flux<ProviderStreamChunk> stream(ProviderRequest request) {
                return Flux.just(new ProviderStreamChunk("", null, null, null, "test"));
            }

            @Override
            public Mono<ProviderCancellationResult> cancel(UUID requestId) {
                return Mono.just(new ProviderCancellationResult(false, false));
            }
        };
        ProviderClientRegistry registry = providerKey -> Mono.just(providerClient);
        LlmConversationCompactionAdapter adapter = new LlmConversationCompactionAdapter(registry);

        StepVerifier.create(adapter.compact(new ConversationCompactionPort.CompactionRequest(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "gemini",
                        "gemini-1.5-flash",
                        0,
                        0,
                        Optional.empty(),
                        List.of(new StartInferenceCommand.Message(MessageRole.USER, "hello")),
                        128)))
                .expectError(IllegalStateException.class)
                .verify();
    }
}
