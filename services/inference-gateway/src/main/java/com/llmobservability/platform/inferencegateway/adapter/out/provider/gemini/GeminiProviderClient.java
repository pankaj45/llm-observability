package com.llmobservability.platform.inferencegateway.adapter.out.provider.gemini;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.llmobservability.platform.inferencegateway.application.port.out.ProviderClient;
import com.llmobservability.platform.inferencegateway.application.service.ApplicationException;
import com.llmobservability.platform.inferencegateway.domain.model.ErrorCode;
import com.llmobservability.platform.inferencegateway.domain.model.FailureStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
class GeminiProviderClient implements ProviderClient {
    private static final Logger log = LoggerFactory.getLogger(GeminiProviderClient.class);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final GeminiProperties properties;

    GeminiProviderClient(WebClient.Builder webClientBuilder, ObjectMapper objectMapper, GeminiProperties properties) {
        this.webClient = webClientBuilder.baseUrl(properties.baseUrl()).build();
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public String providerKey() {
        return "gemini";
    }

    @Override
    public Flux<ProviderStreamChunk> stream(ProviderRequest request) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            return Flux.error(new ApplicationException(
                    ErrorCode.PROVIDER_UNAVAILABLE,
                    FailureStage.PROVIDER,
                    "Gemini API key is not configured"));
        }

        return webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/v1beta/models/{model}:streamGenerateContent")
                        .queryParam("alt", "sse")
                        .queryParam("key", properties.apiKey())
                        .build(request.model()))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(toGeminiRequest(request))
                .retrieve()
                .bodyToFlux(String.class)
                .doOnSubscribe(subscription -> log.debug(
                        "Starting Gemini stream provider=gemini model={} messageCount={}",
                        request.model(),
                        request.messages().size()))
                .doOnNext(chunk -> log.trace(
                        "Received Gemini stream chunk bytes={} lineCount={}",
                        chunk.length(),
                        chunk.lines().count()))
                .flatMapIterable(this::extractDataFrames)
                .map(this::parseChunk)
                .doOnError(error -> {
                    if (error instanceof WebClientResponseException ex) {
                        log.warn(
                                "Gemini stream HTTP error status={} responseBytes={}",
                                ex.getStatusCode().value(),
                                ex.getResponseBodyAsByteArray().length);
                    } else {
                        log.warn("Gemini stream failed errorType={}", error.getClass().getSimpleName());
                    }
                })
                .onErrorMap(error -> error instanceof ApplicationException ? error : new ApplicationException(
                        ErrorCode.INTERNAL_PROVIDER_ERROR,
                        FailureStage.PROVIDER,
                        null,
                        "Gemini stream failed",
                        true,
                        error));
    }

    @Override
    public Mono<ProviderCancellationResult> cancel(UUID requestId) {
        return Mono.just(new ProviderCancellationResult(false, false));
    }

    private Map<String, Object> toGeminiRequest(ProviderRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        List<Map<String, Object>> contents = new ArrayList<>();
        List<String> systemInstructions = new ArrayList<>();

        for (ProviderMessage message : request.messages()) {
            if ("system".equals(message.role())) {
                systemInstructions.add(message.content());
                continue;
            }
            contents.add(Map.of(
                    "role", toGeminiRole(message.role()),
                    "parts", List.of(Map.of("text", message.content()))));
        }

        if (!systemInstructions.isEmpty()) {
            body.put("systemInstruction", Map.of(
                    "parts", List.of(Map.of("text", String.join("\n", systemInstructions)))));
        }
        body.put("contents", contents);
        body.put("generationConfig", generationConfig(request.parameters()));
        return body;
    }

    private Map<String, Object> generationConfig(Map<String, Object> parameters) {
        Map<String, Object> config = new LinkedHashMap<>();
        copy(parameters, config, "temperature");
        copy(parameters, config, "topP");
        copy(parameters, config, "topK");
        copy(parameters, config, "maxOutputTokens");
        return config;
    }

    private void copy(Map<String, Object> from, Map<String, Object> to, String key) {
        if (from != null && from.containsKey(key)) {
            to.put(key, from.get(key));
        }
    }

    private String toGeminiRole(String role) {
        if ("assistant".equals(role)) {
            return "model";
        }
        return "user";
    }

    private List<String> extractDataFrames(String chunk) {
        List<String> frames = chunk.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank() && !"[DONE]".equals(line))
                .map(line -> {
                    if (line.startsWith("data:")) {
                        return line.substring("data:".length()).trim();
                    } else if (line.startsWith("{")) {
                        return line;
                    }
                    log.trace("Skipping Gemini stream line without data prefix or JSON object marker length={}", line.length());
                    return null;
                })
                .filter(line -> line != null && !line.isBlank())
                .toList();
        log.trace("Extracted Gemini data frames count={}", frames.size());
        return frames;
    }

    private ProviderStreamChunk parseChunk(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root.has("error")) {
                JsonNode errorNode = root.path("error");
                String code = errorNode.path("code").asText("unknown");
                String status = errorNode.path("status").asText("unknown");
                throw new ApplicationException(
                        ErrorCode.INTERNAL_PROVIDER_ERROR,
                        FailureStage.PROVIDER,
                        null,
                        "Gemini API returned an error in stream: code=" + code + " status=" + status,
                        true,
                        null);
            }

            String text = root.path("candidates")
                    .path(0)
                    .path("content")
                    .path("parts")
                    .path(0)
                    .path("text")
                    .asText("");
            String finishReason = root.path("candidates").path(0).path("finishReason").asText(null);
            JsonNode usage = root.path("usageMetadata");
            Integer inputTokens = usage.path("promptTokenCount").isMissingNode() ? null : usage.path("promptTokenCount").asInt();
            Integer outputTokens = usage.path("candidatesTokenCount").isMissingNode() ? null : usage.path("candidatesTokenCount").asInt();

            log.trace(
                    "Parsed Gemini stream frame hasText={} finishReason={} inputTokensPresent={} outputTokensPresent={}",
                    !text.isBlank(),
                    finishReason,
                    inputTokens != null,
                    outputTokens != null);

            return new ProviderStreamChunk(text, inputTokens, outputTokens, finishReason, "gemini.sse");
        } catch (Exception e) {
            if (e instanceof ApplicationException applicationException) {
                throw applicationException;
            }
            throw new ApplicationException(
                    ErrorCode.INTERNAL_PROVIDER_ERROR,
                    FailureStage.PROVIDER,
                    null,
                    "Gemini stream frame could not be parsed",
                    true,
                    e);
        }
    }
}
