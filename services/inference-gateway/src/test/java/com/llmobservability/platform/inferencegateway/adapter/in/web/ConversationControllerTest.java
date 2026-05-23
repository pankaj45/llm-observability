package com.llmobservability.platform.inferencegateway.adapter.in.web;

import com.llmobservability.platform.inferencegateway.application.port.in.ContinueConversationCommand;
import com.llmobservability.platform.inferencegateway.application.port.in.ConversationMessageResult;
import com.llmobservability.platform.inferencegateway.application.port.in.ListConversationMessagesQuery;
import com.llmobservability.platform.inferencegateway.application.port.in.InferenceGatewayUseCase;
import com.llmobservability.platform.inferencegateway.application.port.in.PagedResult;
import com.llmobservability.platform.inferencegateway.domain.model.MessageRole;
import com.llmobservability.platform.inferencegateway.domain.model.RedactionState;
import com.llmobservability.platform.inferencegateway.domain.model.StreamEvent;
import com.llmobservability.platform.inferencegateway.domain.model.StreamEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.resource.reactive.ReactiveOAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveUserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@WebFluxTest(
        controllers = ConversationController.class,
        excludeAutoConfiguration = {
                ReactiveSecurityAutoConfiguration.class,
                ReactiveUserDetailsServiceAutoConfiguration.class,
                ReactiveOAuth2ResourceServerAutoConfiguration.class
        })
@TestPropertySource(properties = "spring.jackson.deserialization.fail-on-unknown-properties=true")
class ConversationControllerTest {
    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private InferenceGatewayUseCase useCase;

    @MockBean
    private TenantProjectAuthorizer authorizer;

    @BeforeEach
    void allowAuthorization() {
        when(authorizer.requireTenantProject(any(), any(), any())).thenReturn(Mono.empty());
    }

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

    @Test
    void listMessagesDelegatesWithTenantProjectScope() {
        UUID conversationId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        when(useCase.listConversationMessages(any())).thenReturn(Mono.just(new PagedResult<>(List.of(new ConversationMessageResult(
                messageId,
                conversationId,
                MessageRole.USER,
                0,
                "hello",
                "hash",
                2,
                RedactionState.NONE,
                Map.of("source", "request"),
                Instant.parse("2026-05-23T00:00:00Z"),
                "cursor-1")), null)));

        webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v1/conversations/{conversationId}/messages")
                        .queryParam("tenantId", "tenant-a")
                        .queryParam("projectId", "project-a")
                        .queryParam("limit", "10")
                        .build(conversationId))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.items[0].messageId").isEqualTo(messageId.toString())
                .jsonPath("$.items[0].content").isEqualTo("hello")
                .jsonPath("$.items[0].cursor").isEqualTo("cursor-1");

        ArgumentCaptor<ListConversationMessagesQuery> query = ArgumentCaptor.forClass(ListConversationMessagesQuery.class);
        verify(useCase).listConversationMessages(query.capture());
        assertThat(query.getValue().conversationId()).isEqualTo(conversationId);
        assertThat(query.getValue().tenantId()).isEqualTo("tenant-a");
        assertThat(query.getValue().projectId()).isEqualTo("project-a");
        assertThat(query.getValue().limit()).isEqualTo(10);
    }
}
