package com.llmobservability.platform.analyticsquery;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest
@AutoConfigureWebTestClient
@AutoConfigureObservability
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "llm-observability.analytics.clickhouse.enabled=false",
        "management.health.r2dbc.enabled=false",
        "management.otlp.tracing.endpoint=http://localhost:4318/v1/traces"
})
class AnalyticsQueryApplicationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void exposesHealthEndpoint() {
        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    void exposesPrometheusMetricsEndpoint() {
        webTestClient.get()
                .uri("/actuator/prometheus")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> org.assertj.core.api.Assertions.assertThat(body).contains("jvm_info"));
    }
}
