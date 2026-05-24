package com.llmobservability.platform.inferencegateway.adapter.in.web;

import com.llmobservability.platform.inferencegateway.application.port.in.CancelInferenceResult;
import com.llmobservability.platform.inferencegateway.application.port.in.InferenceGatewayUseCase;
import com.llmobservability.platform.inferencegateway.application.port.in.InferenceStatusResult;
import com.llmobservability.platform.inferencegateway.domain.model.InferenceStatus;
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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@WebFluxTest(
        controllers = InferenceController.class,
        excludeAutoConfiguration = {
                ReactiveSecurityAutoConfiguration.class,
                ReactiveUserDetailsServiceAutoConfiguration.class,
                ReactiveOAuth2ResourceServerAutoConfiguration.class
        })
@TestPropertySource(properties = "spring.jackson.deserialization.fail-on-unknown-properties=true")
class InferenceControllerTest {
    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private InferenceGatewayUseCase useCase;

    @MockBean
    private TenantProjectAuthorizer authorizer;

    @BeforeEach
    void allowAuthorization() {
        when(authorizer.requireTenantProject(any(), any(), any())).thenReturn(Mono.empty());
        when(authorizer.requireScope(any())).thenReturn(Mono.empty());
    }

    @Test
    void streamRejectsClientSuppliedConversationId() {
        webTestClient.post()
                .uri("/v1/inference/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "tenantId": "tenant-a",
                          "projectId": "project-a",
                          "provider": "gemini",
                          "model": "gemini-1.5-flash",
                          "conversationId": "00000000-0000-0000-0000-000000000000",
                          "messages": [{"role": "user", "content": "hello"}],
                          "parameters": {},
                          "idempotencyKey": "idem-1"
                        }
                        """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error.code").isEqualTo("validation.invalid_request");
    }

    @Test
    void streamDelegatesWithoutConversationIdInCommand() {
        UUID requestId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        when(useCase.stream(any())).thenReturn(Flux.just(new StreamEvent(
                requestId + ":1",
                StreamEventType.REQUEST_ACCEPTED,
                requestId,
                conversationId,
                "trace-1",
                1,
                Instant.now(),
                Map.of("status", "ACCEPTED"))));

        webTestClient.post()
                .uri("/v1/inference/stream")
                .header("traceparent", "trace-1")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue("""
                        {
                          "tenantId": "tenant-a",
                          "projectId": "project-a",
                          "provider": "gemini",
                          "model": "gemini-1.5-flash",
                          "messages": [{"role": "user", "content": "hello"}],
                          "parameters": {"temperature": 0.1},
                          "metadata": {"purpose": "test"},
                          "idempotencyKey": "idem-2"
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("request.accepted").contains(conversationId.toString()));
    }

    @Test
    void cancelReturnsCancellationResult() {
        UUID requestId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        when(useCase.status(any())).thenReturn(Mono.just(new InferenceStatusResult(
                requestId,
                conversationId,
                "tenant-a",
                "project-a",
                "gemini",
                "gemini-1.5-flash",
                InferenceStatus.STREAMING,
                Instant.now(),
                Instant.now(),
                null,
                null,
                null,
                null,
                null,
                null,
                Map.of())));
        when(useCase.cancel(any())).thenReturn(Mono.just(new CancelInferenceResult(
                requestId,
                conversationId,
                InferenceStatus.CANCELLED,
                false,
                false)));

        webTestClient.delete()
                .uri("/v1/inference/{requestId}/stream", requestId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.requestId").isEqualTo(requestId.toString())
                .jsonPath("$.conversationId").isEqualTo(conversationId.toString())
                .jsonPath("$.status").isEqualTo("CANCELLED");
    }
}
