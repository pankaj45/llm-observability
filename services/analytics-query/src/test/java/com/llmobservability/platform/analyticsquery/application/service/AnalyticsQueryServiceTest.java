package com.llmobservability.platform.analyticsquery.application.service;

import com.llmobservability.platform.analyticsquery.application.port.out.InferenceAnalyticsRepository;
import com.llmobservability.platform.analyticsquery.application.port.in.SearchInferenceRequestsQuery;
import com.llmobservability.platform.analyticsquery.domain.model.AnalyticsFilter;
import com.llmobservability.platform.analyticsquery.domain.model.AnalyticsWindow;
import com.llmobservability.platform.analyticsquery.domain.model.InferenceRequestDetail;
import com.llmobservability.platform.analyticsquery.domain.model.InferenceRequestRow;
import com.llmobservability.platform.analyticsquery.domain.model.InferenceSummary;
import com.llmobservability.platform.analyticsquery.domain.model.InferenceTotals;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalyticsQueryServiceTest {

    @Test
    void searchRequestsNormalizesLimitAndReturnsOpaqueCursor() {
        TestRepository repository = new TestRepository();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        repository.rows.add(row(first, Instant.parse("2026-05-02T00:00:00Z")));
        repository.rows.add(row(second, Instant.parse("2026-05-01T00:00:00Z")));
        AnalyticsQueryService service = new AnalyticsQueryService(repository, new SimpleMeterRegistry());

        StepVerifier.create(service.searchRequests(new SearchInferenceRequestsQuery(
                        "tenant-a",
                        "project-a",
                        Instant.parse("2026-05-01T00:00:00Z"),
                        Instant.parse("2026-05-03T00:00:00Z"),
                        null,
                        null,
                        null,
                        null,
                        null,
                        1)))
                .assertNext(page -> {
                    assertThat(page.items()).hasSize(1);
                    assertThat(page.items().get(0).requestId()).isEqualTo(first);
                    assertThat(page.items().get(0).cursor()).isNotBlank();
                    assertThat(page.nextCursor()).isEqualTo(page.items().get(0).cursor());
                    assertThat(repository.lastLimit).isEqualTo(2);
                })
                .verifyComplete();
    }

    @Test
    void rejectsQueryWindowsLongerThanThirtyDays() {
        AnalyticsQueryService service = new AnalyticsQueryService(new TestRepository(), new SimpleMeterRegistry());

        assertThatThrownBy(() -> service.searchRequests(new SearchInferenceRequestsQuery(
                "tenant-a",
                "project-a",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-02-15T00:00:00Z"),
                null,
                null,
                null,
                null,
                null,
                50)))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining("30 days");
    }

    private InferenceRequestRow row(UUID requestId, Instant completedAt) {
        return new InferenceRequestRow(
                requestId,
                UUID.randomUUID(),
                "gemini",
                "gemini-1.5-flash",
                "COMPLETED",
                completedAt.minusSeconds(1),
                completedAt,
                1000,
                1,
                2,
                3,
                0,
                null,
                null,
                "corr",
                "trace",
                "");
    }

    private static final class TestRepository implements InferenceAnalyticsRepository {
        final List<InferenceRequestRow> rows = new ArrayList<>();
        int lastLimit;

        @Override
        public Mono<InferenceSummary> summary(AnalyticsFilter filter) {
            return Mono.just(new InferenceSummary(
                    filter.tenantId(),
                    filter.projectId(),
                    new AnalyticsWindow(filter.from(), filter.to()),
                    InferenceTotals.empty(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    false));
        }

        @Override
        public Flux<InferenceRequestRow> searchRequests(AnalyticsFilter filter, String decodedCursor, int limit) {
            lastLimit = limit;
            return Flux.fromIterable(rows.stream().limit(limit).toList());
        }

        @Override
        public Mono<InferenceRequestDetail> findRequestDetail(AnalyticsFilter filter, UUID requestId) {
            return Mono.empty();
        }
    }
}
