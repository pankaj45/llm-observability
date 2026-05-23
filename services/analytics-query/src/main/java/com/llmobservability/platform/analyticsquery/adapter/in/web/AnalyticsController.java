package com.llmobservability.platform.analyticsquery.adapter.in.web;

import com.llmobservability.platform.analyticsquery.application.port.in.AnalyticsQueryUseCase;
import com.llmobservability.platform.analyticsquery.application.port.in.GetInferenceRequestDetailQuery;
import com.llmobservability.platform.analyticsquery.application.port.in.GetInferenceSummaryQuery;
import com.llmobservability.platform.analyticsquery.application.port.in.PagedResult;
import com.llmobservability.platform.analyticsquery.application.port.in.SearchInferenceRequestsQuery;
import com.llmobservability.platform.analyticsquery.domain.model.AnalyticsWindow;
import com.llmobservability.platform.analyticsquery.domain.model.DimensionBreakdown;
import com.llmobservability.platform.analyticsquery.domain.model.ErrorBreakdown;
import com.llmobservability.platform.analyticsquery.domain.model.InferenceLifecycleEvent;
import com.llmobservability.platform.analyticsquery.domain.model.InferenceRequestDetail;
import com.llmobservability.platform.analyticsquery.domain.model.InferenceRequestRow;
import com.llmobservability.platform.analyticsquery.domain.model.InferenceSummary;
import com.llmobservability.platform.analyticsquery.domain.model.InferenceTimeSeriesPoint;
import com.llmobservability.platform.analyticsquery.domain.model.InferenceTotals;
import jakarta.validation.constraints.NotBlank;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/v1/analytics/inference")
public class AnalyticsController {
    private final AnalyticsQueryUseCase useCase;

    AnalyticsController(AnalyticsQueryUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping(path = "/summary", produces = MediaType.APPLICATION_JSON_VALUE)
    Mono<InferenceSummaryResponse> summary(
            @RequestParam @NotBlank String tenantId,
            @RequestParam @NotBlank String projectId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String model,
            @RequestParam(required = false) String status
    ) {
        return useCase.summary(new GetInferenceSummaryQuery(tenantId, projectId, from, to, provider, model, status))
                .map(InferenceSummaryResponse::from);
    }

    @GetMapping(path = "/requests", produces = MediaType.APPLICATION_JSON_VALUE)
    Mono<PagedInferenceRequestsResponse> requests(
            @RequestParam @NotBlank String tenantId,
            @RequestParam @NotBlank String projectId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String model,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String errorCode,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return useCase.searchRequests(new SearchInferenceRequestsQuery(
                        tenantId, projectId, from, to, provider, model, status, errorCode, cursor, limit))
                .map(page -> new PagedInferenceRequestsResponse(page.items().stream().map(InferenceRequestRowResponse::from).toList(), page.nextCursor(), false));
    }

    @GetMapping(path = "/requests/{requestId}", produces = MediaType.APPLICATION_JSON_VALUE)
    Mono<InferenceRequestDetailResponse> requestDetail(
            @PathVariable UUID requestId,
            @RequestParam @NotBlank String tenantId,
            @RequestParam @NotBlank String projectId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        return useCase.requestDetail(new GetInferenceRequestDetailQuery(requestId, tenantId, projectId, from, to))
                .map(InferenceRequestDetailResponse::from);
    }

    public record InferenceSummaryResponse(
            String tenantId,
            String projectId,
            AnalyticsWindow window,
            InferenceTotals totals,
            List<InferenceTimeSeriesPoint> timeSeries,
            List<DimensionBreakdown> providers,
            List<DimensionBreakdown> models,
            List<DimensionBreakdown> statuses,
            List<ErrorBreakdown> topErrors,
            boolean degraded
    ) {
        static InferenceSummaryResponse from(InferenceSummary summary) {
            return new InferenceSummaryResponse(
                    summary.tenantId(),
                    summary.projectId(),
                    summary.window(),
                    summary.totals(),
                    summary.timeSeries(),
                    summary.providers(),
                    summary.models(),
                    summary.statuses(),
                    summary.topErrors(),
                    summary.degraded());
        }
    }

    public record PagedInferenceRequestsResponse(
            List<InferenceRequestRowResponse> items,
            String nextCursor,
            boolean degraded
    ) {
    }

    public record InferenceRequestRowResponse(
            UUID requestId,
            UUID conversationId,
            String provider,
            String model,
            String status,
            Instant startedAt,
            Instant completedAt,
            long durationMs,
            long inputTokens,
            long outputTokens,
            long totalTokens,
            double estimatedCostUsd,
            String errorCode,
            String failureStage,
            String correlationId,
            String traceparent,
            String cursor
    ) {
        static InferenceRequestRowResponse from(InferenceRequestRow row) {
            return new InferenceRequestRowResponse(
                    row.requestId(),
                    row.conversationId(),
                    row.provider(),
                    row.model(),
                    row.status(),
                    row.startedAt(),
                    row.completedAt(),
                    row.durationMs(),
                    row.inputTokens(),
                    row.outputTokens(),
                    row.totalTokens(),
                    row.estimatedCostUsd(),
                    row.errorCode(),
                    row.failureStage(),
                    row.correlationId(),
                    row.traceparent(),
                    row.cursor());
        }
    }

    public record InferenceRequestDetailResponse(
            UUID requestId,
            UUID conversationId,
            String tenantId,
            String projectId,
            String provider,
            String model,
            String status,
            Instant startedAt,
            Instant completedAt,
            long durationMs,
            long inputTokens,
            long outputTokens,
            long totalTokens,
            double estimatedCostUsd,
            String errorCode,
            String failureStage,
            String providerErrorCode,
            Boolean retryable,
            String cancellationReason,
            Boolean providerCancellationAttempted,
            Boolean providerCancellationSucceeded,
            String correlationId,
            String traceparent,
            List<InferenceLifecycleEvent> events
    ) {
        static InferenceRequestDetailResponse from(InferenceRequestDetail detail) {
            return new InferenceRequestDetailResponse(
                    detail.requestId(),
                    detail.conversationId(),
                    detail.tenantId(),
                    detail.projectId(),
                    detail.provider(),
                    detail.model(),
                    detail.status(),
                    detail.startedAt(),
                    detail.completedAt(),
                    detail.durationMs(),
                    detail.inputTokens(),
                    detail.outputTokens(),
                    detail.totalTokens(),
                    detail.estimatedCostUsd(),
                    detail.errorCode(),
                    detail.failureStage(),
                    detail.providerErrorCode(),
                    detail.retryable(),
                    detail.cancellationReason(),
                    detail.providerCancellationAttempted(),
                    detail.providerCancellationSucceeded(),
                    detail.correlationId(),
                    detail.traceparent(),
                    detail.events());
        }
    }
}
