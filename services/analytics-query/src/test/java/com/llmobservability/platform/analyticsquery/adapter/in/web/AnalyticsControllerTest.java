package com.llmobservability.platform.analyticsquery.adapter.in.web;

import com.llmobservability.platform.analyticsquery.application.port.in.AnalyticsQueryUseCase;
import com.llmobservability.platform.analyticsquery.application.port.in.PagedResult;
import com.llmobservability.platform.analyticsquery.domain.model.AnalyticsWindow;
import com.llmobservability.platform.analyticsquery.domain.model.InferenceRequestRow;
import com.llmobservability.platform.analyticsquery.domain.model.InferenceSummary;
import com.llmobservability.platform.analyticsquery.domain.model.InferenceTotals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.resource.reactive.ReactiveOAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveUserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@WebFluxTest(
        controllers = AnalyticsController.class,
        excludeAutoConfiguration = {
                ReactiveSecurityAutoConfiguration.class,
                ReactiveUserDetailsServiceAutoConfiguration.class,
                ReactiveOAuth2ResourceServerAutoConfiguration.class
        })
class AnalyticsControllerTest {
    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private AnalyticsQueryUseCase useCase;

    @MockBean
    private TenantProjectAuthorizer authorizer;

    @BeforeEach
    void allowAuthorization() {
        when(authorizer.requireTenantProject(any(), any(), any())).thenReturn(Mono.empty());
    }

    @Test
    void summaryDelegatesWithTenantProjectAndWindow() {
        when(useCase.summary(any())).thenReturn(Mono.just(new InferenceSummary(
                "tenant-a",
                "project-a",
                new AnalyticsWindow(Instant.parse("2026-05-01T00:00:00Z"), Instant.parse("2026-05-02T00:00:00Z")),
                new InferenceTotals(10, 8, 1, 1, 100, 200, 300, 120.5, 240, 0.1, 0),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                false)));

        webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v1/analytics/inference/summary")
                        .queryParam("tenantId", "tenant-a")
                        .queryParam("projectId", "project-a")
                        .queryParam("from", "2026-05-01T00:00:00Z")
                        .queryParam("to", "2026-05-02T00:00:00Z")
                        .queryParam("provider", "gemini")
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.tenantId").isEqualTo("tenant-a")
                .jsonPath("$.totals.requestCount").isEqualTo(10);

        ArgumentCaptor<com.llmobservability.platform.analyticsquery.application.port.in.GetInferenceSummaryQuery> query =
                ArgumentCaptor.forClass(com.llmobservability.platform.analyticsquery.application.port.in.GetInferenceSummaryQuery.class);
        verify(useCase).summary(query.capture());
        assertThat(query.getValue().tenantId()).isEqualTo("tenant-a");
        assertThat(query.getValue().provider()).isEqualTo("gemini");
    }

    @Test
    void requestSearchReturnsRowsAndCursor() {
        UUID requestId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        when(useCase.searchRequests(any())).thenReturn(Mono.just(new PagedResult<>(List.of(new InferenceRequestRow(
                requestId,
                conversationId,
                "gemini",
                "gemini-1.5-flash",
                "COMPLETED",
                Instant.parse("2026-05-01T00:00:00Z"),
                Instant.parse("2026-05-01T00:00:01Z"),
                1000,
                10,
                20,
                30,
                0,
                null,
                null,
                "corr-1",
                "trace-1",
                "cursor-1")), "cursor-1")));

        webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v1/analytics/inference/requests")
                        .queryParam("tenantId", "tenant-a")
                        .queryParam("projectId", "project-a")
                        .queryParam("from", "2026-05-01T00:00:00Z")
                        .queryParam("to", "2026-05-02T00:00:00Z")
                        .queryParam("limit", "10")
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.items[0].requestId").isEqualTo(requestId.toString())
                .jsonPath("$.items[0].conversationId").isEqualTo(conversationId.toString())
                .jsonPath("$.nextCursor").isEqualTo("cursor-1");
    }
}
