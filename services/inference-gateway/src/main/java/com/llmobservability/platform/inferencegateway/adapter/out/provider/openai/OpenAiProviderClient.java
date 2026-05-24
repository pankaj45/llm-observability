package com.llmobservability.platform.inferencegateway.adapter.out.provider.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.llmobservability.platform.inferencegateway.application.port.out.ProviderClient;
import com.llmobservability.platform.inferencegateway.application.service.ApplicationException;
import com.llmobservability.platform.inferencegateway.domain.model.ErrorCode;
import com.llmobservability.platform.inferencegateway.domain.model.FailureStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
class OpenAiProviderClient implements ProviderClient {
    private static final Logger log = LoggerFactory.getLogger(OpenAiProviderClient.class);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final OpenAiProperties properties;

    OpenAiProviderClient(WebClient.Builder webClientBuilder, ObjectMapper objectMapper, OpenAiProperties properties) {
        this.webClient = webClientBuilder.baseUrl(properties.baseUrl()).build();
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public String providerKey() {
        return "openai";
    }

    @Override
    public Flux<ProviderStreamChunk> stream(ProviderRequest request) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            return Flux.error(new ApplicationException(
                    ErrorCode.PROVIDER_UNAVAILABLE,
                    FailureStage.PROVIDER,
                    "OpenAI API key is not configured"));
        }

        return webClient.post()
                .uri("/v1/responses")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                .headers(headers -> {
                    if (properties.organization() != null && !properties.organization().isBlank()) {
                        headers.add("OpenAI-Organization", properties.organization());
                    }
                    if (properties.project() != null && !properties.project().isBlank()) {
                        headers.add("OpenAI-Project", properties.project());
                    }
                })
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(toOpenAiRequest(request))
                .retrieve()
                .bodyToFlux(String.class)
                .doOnSubscribe(subscription -> log.debug(
                        "Starting OpenAI stream provider=openai model={} messageCount={}",
                        request.model(),
                        request.messages().size()))
                .doOnNext(chunk -> log.trace(
                        "Received OpenAI stream chunk bytes={} lineCount={}",
                        chunk.length(),
                        chunk.lines().count()))
                .flatMapIterable(this::extractDataFrames)
                .map(this::parseChunk)
                .filter(chunk -> chunk.text() != null
                        || chunk.inputTokens() != null
                        || chunk.outputTokens() != null
                        || chunk.finishReason() != null)
                .doOnError(error -> {
                    if (error instanceof WebClientResponseException ex) {
                        log.warn(
                                "OpenAI stream HTTP error status={} responseBytes={}",
                                ex.getStatusCode().value(),
                                ex.getResponseBodyAsByteArray().length);
                    } else {
                        log.warn("OpenAI stream failed errorType={}", error.getClass().getSimpleName());
                    }
                })
                .onErrorMap(error -> error instanceof ApplicationException ? error : mapProviderError(error));
    }

    @Override
    public Mono<ProviderCancellationResult> cancel(UUID requestId) {
        return Mono.just(new ProviderCancellationResult(false, false));
    }

    private Map<String, Object> toOpenAiRequest(ProviderRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        List<Map<String, Object>> input = new ArrayList<>();
        List<String> instructions = new ArrayList<>();

        for (ProviderMessage message : request.messages()) {
            if ("system".equals(message.role())) {
                instructions.add(message.content());
                continue;
            }
            input.add(Map.of(
                    "role", toOpenAiRole(message.role()),
                    "content", message.content()));
        }

        body.put("model", request.model());
        body.put("input", input);
        body.put("stream", true);
        body.put("store", false);
        if (!instructions.isEmpty()) {
            body.put("instructions", String.join("\n", instructions));
        }
        copyParameters(request.parameters(), body);
        return body;
    }

    private void copyParameters(Map<String, Object> parameters, Map<String, Object> body) {
        if (parameters == null) {
            return;
        }
        copy(parameters, body, "temperature", "temperature");
        copy(parameters, body, "topP", "top_p");
        copy(parameters, body, "maxOutputTokens", "max_output_tokens");
        copy(parameters, body, "maxTokens", "max_output_tokens");
        copy(parameters, body, "reasoning", "reasoning");
        copy(parameters, body, "text", "text");
    }

    private void copy(Map<String, Object> from, Map<String, Object> to, String fromKey, String toKey) {
        if (from.containsKey(fromKey)) {
            to.put(toKey, from.get(fromKey));
        }
    }

    private String toOpenAiRole(String role) {
        if ("assistant".equals(role)) {
            return "assistant";
        }
        return "user";
    }

    private List<String> extractDataFrames(String chunk) {
        List<String> frames = chunk.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank() && !"[DONE]".equals(line))
                .filter(line -> line.startsWith("data:") || line.startsWith("{"))
                .map(line -> line.startsWith("data:") ? line.substring("data:".length()).trim() : line)
                .filter(line -> !line.isBlank())
                .toList();
        log.trace("Extracted OpenAI data frames count={}", frames.size());
        return frames;
    }

    private ProviderStreamChunk parseChunk(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            String type = root.path("type").asText("");
            if ("error".equals(type) || root.has("error")) {
                JsonNode errorNode = root.path("error");
                String code = errorNode.path("code").asText("unknown");
                String message = errorNode.path("message").asText("OpenAI stream returned an error");
                throw new ApplicationException(
                        mapOpenAiErrorCode(code),
                        FailureStage.PROVIDER,
                        code,
                        "OpenAI API returned an error: " + message,
                        retryable(code),
                        null);
            }

            if ("response.output_text.delta".equals(type)) {
                return new ProviderStreamChunk(root.path("delta").asText(""), null, null, null, type);
            }
            if ("response.completed".equals(type)) {
                JsonNode response = root.path("response");
                JsonNode usage = response.path("usage");
                Integer inputTokens = usage.path("input_tokens").isMissingNode() ? null : usage.path("input_tokens").asInt();
                Integer outputTokens = usage.path("output_tokens").isMissingNode() ? null : usage.path("output_tokens").asInt();
                String finishReason = response.path("status").asText("completed");
                return new ProviderStreamChunk(null, inputTokens, outputTokens, finishReason, type);
            }
            if ("response.failed".equals(type)) {
                JsonNode response = root.path("response");
                JsonNode errorNode = response.path("error");
                String code = errorNode.path("code").asText("unknown");
                String message = errorNode.path("message").asText("OpenAI response failed");
                throw new ApplicationException(
                        mapOpenAiErrorCode(code),
                        FailureStage.PROVIDER,
                        code,
                        "OpenAI API response failed: " + message,
                        retryable(code),
                        null);
            }

            return new ProviderStreamChunk(null, null, null, null, type.isBlank() ? "openai.sse" : type);
        } catch (Exception e) {
            if (e instanceof ApplicationException applicationException) {
                throw applicationException;
            }
            throw new ApplicationException(
                    ErrorCode.INTERNAL_PROVIDER_ERROR,
                    FailureStage.PROVIDER,
                    null,
                    "OpenAI stream frame could not be parsed",
                    true,
                    e);
        }
    }

    private ApplicationException mapProviderError(Throwable error) {
        if (error instanceof WebClientResponseException ex) {
            ErrorCode errorCode = switch (ex.getStatusCode().value()) {
                case 408, 504 -> ErrorCode.PROVIDER_TIMEOUT;
                case 429 -> ErrorCode.PROVIDER_RATE_LIMITED;
                case 401, 403, 404 -> ErrorCode.PROVIDER_UNAVAILABLE;
                default -> ErrorCode.INTERNAL_PROVIDER_ERROR;
            };
            return new ApplicationException(
                    errorCode,
                    FailureStage.PROVIDER,
                    String.valueOf(ex.getStatusCode().value()),
                    "OpenAI stream failed",
                    ex.getStatusCode().is5xxServerError() || ex.getStatusCode().value() == 429,
                    error);
        }
        return new ApplicationException(
                ErrorCode.INTERNAL_PROVIDER_ERROR,
                FailureStage.PROVIDER,
                null,
                "OpenAI stream failed",
                true,
                error);
    }

    private ErrorCode mapOpenAiErrorCode(String providerCode) {
        if (providerCode == null) {
            return ErrorCode.INTERNAL_PROVIDER_ERROR;
        }
        return switch (providerCode) {
            case "rate_limit_exceeded", "rate_limit_error" -> ErrorCode.PROVIDER_RATE_LIMITED;
            case "server_error", "temporarily_unavailable", "service_unavailable" -> ErrorCode.PROVIDER_UNAVAILABLE;
            case "timeout" -> ErrorCode.PROVIDER_TIMEOUT;
            default -> ErrorCode.INTERNAL_PROVIDER_ERROR;
        };
    }

    private boolean retryable(String providerCode) {
        return providerCode == null
                || "rate_limit_exceeded".equals(providerCode)
                || "rate_limit_error".equals(providerCode)
                || "server_error".equals(providerCode)
                || "temporarily_unavailable".equals(providerCode)
                || "service_unavailable".equals(providerCode)
                || "timeout".equals(providerCode);
    }
}
