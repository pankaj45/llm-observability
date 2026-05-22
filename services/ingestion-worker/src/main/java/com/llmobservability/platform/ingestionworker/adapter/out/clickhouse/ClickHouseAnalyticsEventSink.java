package com.llmobservability.platform.ingestionworker.adapter.out.clickhouse;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.llmobservability.platform.ingestionworker.application.port.out.AnalyticsEventSink;
import com.llmobservability.platform.ingestionworker.domain.model.AnalyticsLifecycleFact;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
@ConditionalOnProperty(prefix = "llm-observability.ingestion.clickhouse", name = "enabled", havingValue = "true")
class ClickHouseAnalyticsEventSink implements AnalyticsEventSink {
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String database;
    private final String table;

    ClickHouseAnalyticsEventSink(
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            @Value("${llm-observability.ingestion.clickhouse.base-url}") String baseUrl,
            @Value("${llm-observability.ingestion.clickhouse.username}") String username,
            @Value("${llm-observability.ingestion.clickhouse.password}") String password,
            @Value("${llm-observability.ingestion.clickhouse.database}") String database,
            @Value("${llm-observability.ingestion.clickhouse.lifecycle-table}") String table
    ) {
        this.webClient = webClientBuilder
                .baseUrl(baseUrl)
                .defaultHeaders(headers -> headers.setBasicAuth(username, password))
                .build();
        this.objectMapper = objectMapper.copy().setSerializationInclusion(JsonInclude.Include.NON_NULL);
        this.database = database;
        this.table = table;
    }

    @Override
    public Mono<Void> write(AnalyticsLifecycleFact fact) {
        String sql = "INSERT INTO " + database + "." + table
                + " SETTINGS date_time_input_format='best_effort' FORMAT JSONEachRow\n"
                + serialize(fact);
        return webClient.post()
                .contentType(MediaType.TEXT_PLAIN)
                .bodyValue(sql)
                .retrieve()
                .bodyToMono(Void.class);
    }

    private String serialize(AnalyticsLifecycleFact fact) {
        try {
            return objectMapper.writeValueAsString(fact);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Unable to serialize ClickHouse lifecycle fact", e);
        }
    }
}
