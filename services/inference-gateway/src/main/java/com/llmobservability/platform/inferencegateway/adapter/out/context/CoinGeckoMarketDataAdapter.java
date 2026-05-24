package com.llmobservability.platform.inferencegateway.adapter.out.context;

import com.fasterxml.jackson.databind.JsonNode;
import com.llmobservability.platform.inferencegateway.application.port.out.MarketDataPort;
import com.llmobservability.platform.inferencegateway.application.service.context.ContextEvidence;
import com.llmobservability.platform.inferencegateway.config.ContextOrchestratorProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import io.netty.channel.ChannelOption;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Component
class CoinGeckoMarketDataAdapter implements MarketDataPort {
    private final WebClient webClient;
    private final ContextOrchestratorProperties properties;

    CoinGeckoMarketDataAdapter(WebClient.Builder webClientBuilder, ContextOrchestratorProperties properties) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .responseTimeout(Duration.ofSeconds(5));
        this.webClient = webClientBuilder.clone()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .baseUrl(properties.getCoinGecko().getBaseUrl())
                .build();
        this.properties = properties;
    }

    @Override
    public Mono<List<ContextEvidence>> lookup(String query) {
        if (!properties.getCoinGecko().isEnabled()) {
            return Mono.error(new IllegalStateException("CoinGecko market data is disabled"));
        }
        String coinId = coinId(query);
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v3/simple/price")
                        .queryParam("ids", coinId)
                        .queryParam("vs_currencies", "usd")
                        .queryParam("include_market_cap", "true")
                        .queryParam("include_24hr_vol", "true")
                        .queryParam("include_24hr_change", "true")
                        .queryParam("include_last_updated_at", "true")
                        .build())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(body -> evidence(coinId, body.path(coinId)))
                .map(List::of);
    }

    private String coinId(String query) {
        String lower = query == null ? "" : query.toLowerCase(Locale.ROOT);
        if (lower.contains("eth") || lower.contains("ethereum")) {
            return "ethereum";
        }
        return "bitcoin";
    }

    private ContextEvidence evidence(String coinId, JsonNode node) {
        Instant fetchedAt = Instant.now();
        String title = coinId.substring(0, 1).toUpperCase(Locale.ROOT) + coinId.substring(1) + " market data";
        String content = """
                Asset: %s
                USD price: %s
                USD market cap: %s
                24h volume: %s
                24h change percent: %s
                Provider last updated epoch seconds: %s
                """.formatted(
                coinId,
                numberText(node, "usd"),
                numberText(node, "usd_market_cap"),
                numberText(node, "usd_24h_vol"),
                numberText(node, "usd_24h_change"),
                numberText(node, "last_updated_at"));
        return new ContextEvidence(
                UUID.randomUUID().toString(),
                "marketData.lookup",
                "CoinGecko",
                "https://www.coingecko.com/en/coins/" + coinId,
                title,
                fetchedAt,
                null,
                content,
                "high",
                Duration.ofSeconds(60));
    }

    private String numberText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? "unavailable" : value.asText();
    }
}
