package com.llmobservability.platform.inferencegateway.adapter.out.context;

import com.fasterxml.jackson.databind.JsonNode;
import com.llmobservability.platform.inferencegateway.application.port.out.WebSearchPort;
import com.llmobservability.platform.inferencegateway.application.service.context.ContextEvidence;
import com.llmobservability.platform.inferencegateway.config.ContextOrchestratorProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import io.netty.channel.ChannelOption;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
class TavilyWebSearchAdapter implements WebSearchPort {
    private final WebClient webClient;
    private final ContextOrchestratorProperties properties;

    TavilyWebSearchAdapter(WebClient.Builder webClientBuilder, ContextOrchestratorProperties properties) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .responseTimeout(Duration.ofSeconds(5));
        this.webClient = webClientBuilder.clone()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .baseUrl(properties.getTavily().getBaseUrl())
                .build();
        this.properties = properties;
    }

    @Override
    public Mono<List<ContextEvidence>> search(String query, int maxResults) {
        if (!properties.getTavily().isEnabled()) {
            return Mono.error(new IllegalStateException("Tavily web search is disabled"));
        }
        if (properties.getTavily().getApiKey() == null || properties.getTavily().getApiKey().isBlank()) {
            return Mono.error(new IllegalStateException("Tavily API key is not configured"));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("api_key", properties.getTavily().getApiKey());
        body.put("query", query);
        body.put("search_depth", "basic");
        body.put("include_answer", false);
        body.put("include_raw_content", false);
        body.put("max_results", Math.max(1, Math.min(maxResults, 10)));

        return webClient.post()
                .uri("/search")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(this::evidence);
    }

    private List<ContextEvidence> evidence(JsonNode body) {
        Instant fetchedAt = Instant.now();
        List<ContextEvidence> evidence = new ArrayList<>();
        for (JsonNode result : body.path("results")) {
            String url = result.path("url").asText("");
            if (!isPublicHttpUrl(url)) {
                continue;
            }
            evidence.add(new ContextEvidence(
                    UUID.randomUUID().toString(),
                    "webSearch.search",
                    "Tavily",
                    url,
                    result.path("title").asText("Search result"),
                    fetchedAt,
                    null,
                    result.path("content").asText(""),
                    "medium",
                    Duration.ofMinutes(10)));
        }
        return evidence;
    }

    private boolean isPublicHttpUrl(String url) {
        String lower = url == null ? "" : url.toLowerCase();
        return (lower.startsWith("https://") || lower.startsWith("http://"))
                && !lower.contains("localhost")
                && !lower.contains("127.0.0.1")
                && !lower.contains("169.254.169.254");
    }
}
