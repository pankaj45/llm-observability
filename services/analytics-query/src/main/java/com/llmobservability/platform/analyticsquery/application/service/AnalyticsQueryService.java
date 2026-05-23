package com.llmobservability.platform.analyticsquery.application.service;

import com.llmobservability.platform.analyticsquery.application.port.in.AnalyticsQueryUseCase;
import com.llmobservability.platform.analyticsquery.application.port.in.GetInferenceRequestDetailQuery;
import com.llmobservability.platform.analyticsquery.application.port.in.GetInferenceSummaryQuery;
import com.llmobservability.platform.analyticsquery.application.port.in.PagedResult;
import com.llmobservability.platform.analyticsquery.application.port.in.SearchInferenceRequestsQuery;
import com.llmobservability.platform.analyticsquery.application.port.out.InferenceAnalyticsRepository;
import com.llmobservability.platform.analyticsquery.domain.model.AnalyticsFilter;
import com.llmobservability.platform.analyticsquery.domain.model.InferenceRequestDetail;
import com.llmobservability.platform.analyticsquery.domain.model.InferenceRequestRow;
import com.llmobservability.platform.analyticsquery.domain.model.InferenceSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
public class AnalyticsQueryService implements AnalyticsQueryUseCase {
    private static final int DEFAULT_PAGE_LIMIT = 50;
    private static final int MAX_PAGE_LIMIT = 200;
    private static final Duration MAX_QUERY_WINDOW = Duration.ofDays(30);

    private final InferenceAnalyticsRepository analyticsRepository;
    private final MeterRegistry meterRegistry;

    public AnalyticsQueryService(InferenceAnalyticsRepository analyticsRepository, MeterRegistry meterRegistry) {
        this.analyticsRepository = analyticsRepository;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public Mono<InferenceSummary> summary(GetInferenceSummaryQuery query) {
        Timer.Sample sample = Timer.start(meterRegistry);
        AnalyticsFilter filter = filter(query.tenantId(), query.projectId(), query.from(), query.to(), query.provider(), query.model(), query.status(), null);
        return analyticsRepository.summary(filter)
                .doFinally(signal -> sample.stop(Timer.builder("analytics_query_duration_seconds")
                        .description("Analytics query duration")
                        .tag("query", "summary")
                        .register(meterRegistry)));
    }

    @Override
    public Mono<PagedResult<InferenceRequestRow>> searchRequests(SearchInferenceRequestsQuery query) {
        Timer.Sample sample = Timer.start(meterRegistry);
        int limit = normalizeLimit(query.limit());
        String decodedCursor = CursorCodec.decode("request", query.cursor());
        AnalyticsFilter filter = filter(query.tenantId(), query.projectId(), query.from(), query.to(), query.provider(), query.model(), query.status(), query.errorCode());
        return analyticsRepository.searchRequests(filter, decodedCursor, limit + 1)
                .collectList()
                .map(items -> page(items, limit))
                .doFinally(signal -> sample.stop(Timer.builder("analytics_query_duration_seconds")
                        .description("Analytics query duration")
                        .tag("query", "requests")
                        .register(meterRegistry)));
    }

    @Override
    public Mono<InferenceRequestDetail> requestDetail(GetInferenceRequestDetailQuery query) {
        Timer.Sample sample = Timer.start(meterRegistry);
        AnalyticsFilter filter = filter(query.tenantId(), query.projectId(), query.from(), query.to(), null, null, null, null);
        return analyticsRepository.findRequestDetail(filter, query.requestId())
                .switchIfEmpty(Mono.error(new ApplicationException("ANALYTICS_REQUEST_NOT_FOUND", HttpStatus.NOT_FOUND, "Inference request was not found")))
                .doFinally(signal -> sample.stop(Timer.builder("analytics_query_duration_seconds")
                        .description("Analytics query duration")
                        .tag("query", "request-detail")
                        .register(meterRegistry)));
    }

    private AnalyticsFilter filter(String tenantId, String projectId, Instant from, Instant to, String provider, String model, String status, String errorCode) {
        requireText("tenantId", tenantId);
        requireText("projectId", projectId);
        if (from == null || to == null) {
            throw new ApplicationException("VALIDATION_INVALID_REQUEST", HttpStatus.BAD_REQUEST, "from and to are required");
        }
        if (!from.isBefore(to)) {
            throw new ApplicationException("VALIDATION_INVALID_REQUEST", HttpStatus.BAD_REQUEST, "from must be before to");
        }
        if (Duration.between(from, to).compareTo(MAX_QUERY_WINDOW) > 0) {
            throw new ApplicationException("VALIDATION_INVALID_REQUEST", HttpStatus.BAD_REQUEST, "query window must not exceed 30 days");
        }
        return new AnalyticsFilter(
                tenantId,
                projectId,
                from,
                to,
                blankToNull(provider),
                blankToNull(model),
                normalizeStatus(status),
                blankToNull(errorCode));
    }

    private void requireText(String field, String value) {
        if (value == null || value.isBlank()) {
            throw new ApplicationException("VALIDATION_INVALID_REQUEST", HttpStatus.BAD_REQUEST, field + " must not be blank");
        }
    }

    private String normalizeStatus(String status) {
        String normalized = blankToNull(status);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private int normalizeLimit(int requested) {
        if (requested <= 0) {
            return DEFAULT_PAGE_LIMIT;
        }
        return Math.min(requested, MAX_PAGE_LIMIT);
    }

    private PagedResult<InferenceRequestRow> page(List<InferenceRequestRow> items, int limit) {
        boolean hasNext = items.size() > limit;
        List<InferenceRequestRow> page = (hasNext ? items.subList(0, limit) : items).stream()
                .map(item -> item.withCursor(CursorCodec.encode("request", item.completedAtOrStartedAt() + "|" + item.requestId())))
                .toList();
        String nextCursor = hasNext && !page.isEmpty() ? page.get(page.size() - 1).cursor() : null;
        return new PagedResult<>(page, nextCursor);
    }
}
