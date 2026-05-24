package com.llmobservability.platform.inferencegateway.adapter.out.provider.openai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.llmobservability.platform.inferencegateway.application.port.out.ProviderClient;
import com.llmobservability.platform.inferencegateway.application.service.ApplicationException;
import com.llmobservability.platform.inferencegateway.domain.model.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiProviderClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void streamsResponsesApiTextAndUsageEvents() {
        String stream = """
                data: {"type":"response.created","sequence_number":1}

                data: {"type":"response.output_text.delta","sequence_number":2,"delta":"Hel"}

                data: {"type":"response.output_text.delta","sequence_number":3,"delta":"lo"}

                data: {"type":"response.completed","sequence_number":4,"response":{"status":"completed","usage":{"input_tokens":7,"output_tokens":2}}}

                data: [DONE]

                """;
        OpenAiProviderClient client = clientWithResponse(stream);

        StepVerifier.create(client.stream(request()))
                .assertNext(chunk -> {
                    assertThat(chunk.text()).isEqualTo("Hel");
                    assertThat(chunk.providerEventType()).isEqualTo("response.output_text.delta");
                })
                .assertNext(chunk -> assertThat(chunk.text()).isEqualTo("lo"))
                .assertNext(chunk -> {
                    assertThat(chunk.text()).isNull();
                    assertThat(chunk.inputTokens()).isEqualTo(7);
                    assertThat(chunk.outputTokens()).isEqualTo(2);
                    assertThat(chunk.finishReason()).isEqualTo("completed");
                })
                .verifyComplete();
    }

    @Test
    void mapsResponsesApiErrorEvents() {
        String stream = """
                data: {"type":"error","error":{"code":"rate_limit_exceeded","message":"Too many requests"}}

                """;
        OpenAiProviderClient client = clientWithResponse(stream);

        StepVerifier.create(client.stream(request()))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ApplicationException.class);
                    ApplicationException exception = (ApplicationException) error;
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.PROVIDER_RATE_LIMITED);
                    assertThat(exception.providerErrorCode()).isEqualTo("rate_limit_exceeded");
                })
                .verify();
    }

    @Test
    void failsWhenApiKeyIsMissing() {
        OpenAiProviderClient client = new OpenAiProviderClient(
                WebClient.builder(),
                objectMapper,
                new OpenAiProperties("", "https://api.openai.com", "", "", "gpt-5.5"));

        StepVerifier.create(client.stream(request()))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ApplicationException.class);
                    assertThat(((ApplicationException) error).errorCode()).isEqualTo(ErrorCode.PROVIDER_UNAVAILABLE);
                })
                .verify();
    }

    private OpenAiProviderClient clientWithResponse(String body) {
        WebClient.Builder builder = WebClient.builder()
                .exchangeFunction(request -> {
                    assertThat(request.url().getPath()).isEqualTo("/v1/responses");
                    assertThat(request.headers().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer test-key");
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_EVENT_STREAM_VALUE)
                            .body(body)
                            .build());
                });
        return new OpenAiProviderClient(
                builder,
                objectMapper,
                new OpenAiProperties("test-key", "https://api.openai.com", "", "", "gpt-5.5"));
    }

    private ProviderClient.ProviderRequest request() {
        return new ProviderClient.ProviderRequest(
                UUID.randomUUID(),
                "gpt-5.5",
                List.of(
                        new ProviderClient.ProviderMessage("system", "Be concise."),
                        new ProviderClient.ProviderMessage("user", "Say hello.")),
                Map.of("temperature", 0.2, "maxOutputTokens", 128));
    }
}
