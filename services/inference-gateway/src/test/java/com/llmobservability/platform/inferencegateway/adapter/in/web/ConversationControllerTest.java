package com.llmobservability.platform.inferencegateway.adapter.in.web;

import com.llmobservability.platform.inferencegateway.application.port.in.ContinueConversationCommand;
import com.llmobservability.platform.inferencegateway.application.port.in.InferenceGatewayUseCase;
import com.llmobservability.platform.inferencegateway.domain.model.MessageRole;
import com.llmobservability.platform.inferencegateway.domain.model.StreamEvent;
import com.llmobservability.platform.inferencegateway.domain.model.StreamEventType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = ConversationController.class)
@TestPropertySource(properties = "spring.jackson.deserialization.fail-on-unknown-properties=true")
class ConversationControllerTest {
    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private InferenceGatewayUseCase useCase;

    @Test
    void continueStreamDelegatesWithConversationIdFromPath() {
        UUID requestId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        when(useCase.continueConversation(any())).thenReturn(Flux.just(new StreamEvent(
                requestId + ":1",
                StreamEventType.REQUEST_ACCEPTED,
                requestId,
                conversationId,
                "trace-continue",
                1,
                Instant.now(),
                Map.of("status", "ACCEPTED"))));

        webTestClient.post()
                .uri("/v1/conversations/{conversationId}/messages/stream", conversationId)
                .header("traceparent", "trace-continue")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue("""
                        {
                          "tenantId": "tenant-a",
                          "projectId": "project-a",
                          "provider": "gemini",
                          "model": "gemini-1.5-flash",
                          "messages": [{"role": "user", "content": "continue please"}],
                          "parameters": {"temperature": 0.1},
                          "metadata": {"purpose": "test"},
                          "idempotencyKey": "continue-1"
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("request.accepted").contains(conversationId.toString()));

        ArgumentCaptor<ContinueConversationCommand> command = ArgumentCaptor.forClass(ContinueConversationCommand.class);
        verify(useCase).continueConversation(command.capture());
        assertThat(command.getValue().conversationId()).isEqualTo(conversationId);
        assertThat(command.getValue().messages()).singleElement()
                .satisfies(message -> {
                    assertThat(message.role()).isEqualTo(MessageRole.USER);
                    assertThat(message.content()).isEqualTo("continue please");
                });
    }
}
