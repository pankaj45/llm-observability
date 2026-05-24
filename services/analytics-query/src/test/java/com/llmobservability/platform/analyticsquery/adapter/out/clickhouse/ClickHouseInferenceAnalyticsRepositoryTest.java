package com.llmobservability.platform.analyticsquery.adapter.out.clickhouse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.llmobservability.platform.analyticsquery.domain.model.AnalyticsFilter;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ClickHouseInferenceAnalyticsRepositoryTest {

    @Test
    void requestSearchQualifiesProviderAndModelFiltersToAvoidAggregateAliasCollisions() {
        ClickHouseInferenceAnalyticsRepository repository = new ClickHouseInferenceAnalyticsRepository(
                WebClient.builder(),
                new ObjectMapper(),
                new ClickHouseProperties());
        AnalyticsFilter filter = new AnalyticsFilter(
                "tenant-a",
                "project-a",
                Instant.parse("2026-05-01T00:00:00Z"),
                Instant.parse("2026-05-02T00:00:00Z"),
                "gemini",
                "gemini-2.5-flash",
                null,
                null);

        String sql = repository.requestSearchSql(filter, null, 50);

        assertThat(sql).contains("FROM llm_observability.inference_lifecycle_fact AS lifecycle");
        assertThat(sql).contains("lifecycle.provider = 'gemini'");
        assertThat(sql).contains("lifecycle.model = 'gemini-2.5-flash'");
        assertThat(sql).doesNotContain(" AND provider = 'gemini'");
        assertThat(sql).doesNotContain(" AND model = 'gemini-2.5-flash'");
    }
}
