package com.llmobservability.platform.analyticsquery.adapter.out.clickhouse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.llmobservability.platform.analyticsquery.application.port.out.InferenceAnalyticsRepository;
import com.llmobservability.platform.analyticsquery.application.service.ApplicationException;
import com.llmobservability.platform.analyticsquery.domain.model.AnalyticsFilter;
import com.llmobservability.platform.analyticsquery.domain.model.AnalyticsWindow;
import com.llmobservability.platform.analyticsquery.domain.model.DimensionBreakdown;
import com.llmobservability.platform.analyticsquery.domain.model.ErrorBreakdown;
import com.llmobservability.platform.analyticsquery.domain.model.InferenceLifecycleEvent;
import com.llmobservability.platform.analyticsquery.domain.model.InferenceRequestDetail;
import com.llmobservability.platform.analyticsquery.domain.model.InferenceRequestRow;
import com.llmobservability.platform.analyticsquery.domain.model.InferenceSummary;
import com.llmobservability.platform.analyticsquery.domain.model.InferenceTimeSeriesPoint;
import com.llmobservability.platform.analyticsquery.domain.model.InferenceTotals;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Component
@ConditionalOnProperty(prefix = "llm-observability.analytics.clickhouse", name = "enabled", havingValue = "true", matchIfMissing = true)
class ClickHouseInferenceAnalyticsRepository implements InferenceAnalyticsRepository {
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String table;

    ClickHouseInferenceAnalyticsRepository(WebClient.Builder webClientBuilder, ObjectMapper objectMapper, ClickHouseProperties properties) {
        this.webClient = webClientBuilder
                .baseUrl(properties.getBaseUrl())
                .defaultHeaders(headers -> headers.setBasicAuth(properties.getUsername(), properties.getPassword()))
                .build();
        this.objectMapper = objectMapper;
        this.table = properties.getDatabase() + "." + properties.getLifecycleTable();
    }

    @Override
    public Mono<InferenceSummary> summary(AnalyticsFilter filter) {
        return Mono.zip(
                        queryOne(summarySql(filter)).map(this::toTotalsRow).defaultIfEmpty(new TotalsRow()),
                        queryMany(timeSeriesSql(filter)).collectList(),
                        queryMany(dimensionSql(filter, "provider")).collectList(),
                        queryMany(dimensionSql(filter, "model")).collectList(),
                        queryMany(statusSql(filter)).collectList(),
                        queryMany(errorSql(filter)).collectList()
                )
                .map(tuple -> {
                    TotalsRow totals = tuple.getT1();
                    long requestCount = totals.requestCount();
                    return new InferenceSummary(
                            filter.tenantId(),
                            filter.projectId(),
                            new AnalyticsWindow(filter.from(), filter.to()),
                            new InferenceTotals(
                                    requestCount,
                                    totals.successCount(),
                                    totals.failedCount(),
                                    totals.cancelledCount(),
                                    totals.inputTokens(),
                                    totals.outputTokens(),
                                    totals.totalTokens(),
                                    totals.averageDurationMs(),
                                    totals.p95DurationMs(),
                                    requestCount == 0 ? 0 : (double) totals.failedCount() / requestCount,
                                    0),
                            tuple.getT2().stream().map(this::toTimeSeriesPoint).toList(),
                            tuple.getT3().stream().map(row -> toDimensionBreakdown(row, requestCount)).toList(),
                            tuple.getT4().stream().map(row -> toDimensionBreakdown(row, requestCount)).toList(),
                            tuple.getT5().stream().map(row -> toDimensionBreakdown(row, requestCount)).toList(),
                            tuple.getT6().stream().map(this::toErrorBreakdown).toList(),
                            false);
                })
                .onErrorMap(error -> new ApplicationException("ANALYTICS_QUERY_FAILED", HttpStatus.INTERNAL_SERVER_ERROR, "Analytics summary query failed"));
    }

    @Override
    public Flux<InferenceRequestRow> searchRequests(AnalyticsFilter filter, String decodedCursor, int limit) {
        return queryMany(requestSearchSql(filter, decodedCursor, limit))
                .map(this::toRequestRow)
                .onErrorMap(error -> new ApplicationException("ANALYTICS_QUERY_FAILED", HttpStatus.INTERNAL_SERVER_ERROR, "Analytics request search failed"));
    }

    @Override
    public Mono<InferenceRequestDetail> findRequestDetail(AnalyticsFilter filter, UUID requestId) {
        return queryMany(requestDetailSql(filter, requestId))
                .map(this::toLifecycleRow)
                .collectList()
                .filter(rows -> !rows.isEmpty())
                .map(rows -> toRequestDetail(filter, rows))
                .onErrorMap(error -> error instanceof ApplicationException ? error : new ApplicationException("ANALYTICS_QUERY_FAILED", HttpStatus.INTERNAL_SERVER_ERROR, "Analytics request detail query failed"));
    }

    private Mono<JsonNode> queryOne(String sql) {
        return queryMany(sql).next();
    }

    private Flux<JsonNode> queryMany(String sql) {
        return webClient.post()
                .contentType(MediaType.TEXT_PLAIN)
                .bodyValue(sql + "\nFORMAT JSONEachRow")
                .retrieve()
                .bodyToMono(String.class)
                .flatMapMany(this::parseRows);
    }

    private Flux<JsonNode> parseRows(String body) {
        if (body == null || body.isBlank()) {
            return Flux.empty();
        }
        List<JsonNode> rows = new ArrayList<>();
        for (String line : body.split("\\R")) {
            if (!line.isBlank()) {
                try {
                    rows.add(objectMapper.readTree(line));
                } catch (IOException e) {
                    return Flux.error(e);
                }
            }
        }
        return Flux.fromIterable(rows);
    }

    private String summarySql(AnalyticsFilter filter) {
        return """
                SELECT
                  uniqExact(request_id) AS request_count,
                  uniqExactIf(request_id, event_name = 'inference.completed') AS success_count,
                  uniqExactIf(request_id, event_name = 'inference.failed') AS failed_count,
                  uniqExactIf(request_id, event_name = 'inference.cancelled') AS cancelled_count,
                  sumIf(ifNull(input_tokens, 0), event_name = 'inference.completed') AS input_tokens,
                  sumIf(ifNull(output_tokens, 0), event_name = 'inference.completed') AS output_tokens,
                  sumIf(ifNull(total_tokens, 0), event_name = 'inference.completed') AS total_tokens,
                  ifNull(avgIf(duration_ms, event_name = 'inference.completed'), 0) AS average_duration_ms,
                  ifNull(quantileExactIf(0.95)(duration_ms, event_name = 'inference.completed'), 0) AS p95_duration_ms
                FROM %s
                WHERE %s
                """.formatted(table, where(filter));
    }

    private String timeSeriesSql(AnalyticsFilter filter) {
        return """
                SELECT
                  toStartOfHour(occurred_at) AS bucket,
                  uniqExact(request_id) AS request_count,
                  uniqExactIf(request_id, event_name = 'inference.completed') AS success_count,
                  uniqExactIf(request_id, event_name = 'inference.failed') AS failed_count,
                  uniqExactIf(request_id, event_name = 'inference.cancelled') AS cancelled_count,
                  ifNull(avgIf(duration_ms, event_name = 'inference.completed'), 0) AS average_duration_ms,
                  ifNull(quantileExactIf(0.95)(duration_ms, event_name = 'inference.completed'), 0) AS p95_duration_ms,
                  sumIf(ifNull(total_tokens, 0), event_name = 'inference.completed') AS total_tokens
                FROM %s
                WHERE %s
                GROUP BY bucket
                ORDER BY bucket ASC
                """.formatted(table, where(filter));
    }

    private String dimensionSql(AnalyticsFilter filter, String dimension) {
        return """
                SELECT
                  %s AS name,
                  uniqExact(request_id) AS request_count,
                  ifNull(avgIf(duration_ms, event_name = 'inference.completed'), 0) AS average_duration_ms,
                  sumIf(ifNull(total_tokens, 0), event_name = 'inference.completed') AS total_tokens
                FROM %s
                WHERE %s
                GROUP BY name
                ORDER BY request_count DESC, name ASC
                LIMIT 10
                """.formatted(dimension, table, where(filter));
    }

    private String statusSql(AnalyticsFilter filter) {
        return """
                SELECT
                  status AS name,
                  uniqExact(request_id) AS request_count,
                  ifNull(avgIf(duration_ms, event_name = 'inference.completed'), 0) AS average_duration_ms,
                  sumIf(ifNull(total_tokens, 0), event_name = 'inference.completed') AS total_tokens
                FROM %s
                WHERE %s
                GROUP BY name
                ORDER BY request_count DESC, name ASC
                """.formatted(table, where(filter));
    }

    private String errorSql(AnalyticsFilter filter) {
        return """
                SELECT
                  ifNull(error_code, 'UNKNOWN') AS error_code,
                  any(failure_stage) AS failure_stage,
                  any(provider_error_code) AS provider_error_code,
                  uniqExact(request_id) AS request_count,
                  uniqExactIf(request_id, retryable = true) AS retryable_count
                FROM %s
                WHERE %s AND event_name = 'inference.failed'
                GROUP BY error_code
                ORDER BY request_count DESC, error_code ASC
                LIMIT 10
                """.formatted(table, where(filter));
    }

    String requestSearchSql(AnalyticsFilter filter, String decodedCursor, int limit) {
        String having = searchHaving(filter, decodedCursor);
        return """
                SELECT
                  request_id,
                  any(conversation_id) AS conversation_id,
                  any(provider) AS provider,
                  any(model) AS model,
                  argMax(status, occurred_at) AS status,
                  min(occurred_at) AS started_at,
                  max(occurred_at) AS completed_at,
                  max(ifNull(duration_ms, 0)) AS duration_ms,
                  max(ifNull(input_tokens, 0)) AS input_tokens,
                  max(ifNull(output_tokens, 0)) AS output_tokens,
                  max(ifNull(total_tokens, 0)) AS total_tokens,
                  argMax(error_code, occurred_at) AS error_code,
                  argMax(failure_stage, occurred_at) AS failure_stage,
                  any(correlation_id) AS correlation_id,
                  any(traceparent) AS traceparent
                FROM %s AS lifecycle
                WHERE %s
                GROUP BY request_id
                %s
                ORDER BY completed_at DESC, request_id DESC
                LIMIT %d
                """.formatted(table, where(filter, false, "lifecycle"), having, limit);
    }

    private String requestDetailSql(AnalyticsFilter filter, UUID requestId) {
        return """
                SELECT
                  event_id,
                  event_name,
                  tenant_id,
                  project_id,
                  request_id,
                  conversation_id,
                  provider,
                  model,
                  status,
                  occurred_at,
                  duration_ms,
                  input_tokens,
                  output_tokens,
                  total_tokens,
                  failure_stage,
                  error_code,
                  provider_error_code,
                  retryable,
                  cancellation_reason,
                  provider_cancellation_attempted,
                  provider_cancellation_succeeded,
                  correlation_id,
                  traceparent
                FROM %s
                WHERE %s AND request_id = %s
                ORDER BY occurred_at ASC, event_name ASC
                """.formatted(table, where(filter), sqlString(requestId.toString()));
    }

    private String where(AnalyticsFilter filter) {
        return where(filter, true);
    }

    private String where(AnalyticsFilter filter, boolean includeAggregateFilters) {
        return where(filter, includeAggregateFilters, null);
    }

    private String where(AnalyticsFilter filter, boolean includeAggregateFilters, String qualifier) {
        List<String> clauses = new ArrayList<>();
        clauses.add(column(qualifier, "tenant_id") + " = " + sqlString(filter.tenantId()));
        clauses.add(column(qualifier, "project_id") + " = " + sqlString(filter.projectId()));
        clauses.add(column(qualifier, "occurred_at") + " >= parseDateTime64BestEffort(" + sqlString(filter.from().toString()) + ")");
        clauses.add(column(qualifier, "occurred_at") + " < parseDateTime64BestEffort(" + sqlString(filter.to().toString()) + ")");
        if (filter.provider() != null) {
            clauses.add(column(qualifier, "provider") + " = " + sqlString(filter.provider()));
        }
        if (filter.model() != null) {
            clauses.add(column(qualifier, "model") + " = " + sqlString(filter.model()));
        }
        if (includeAggregateFilters && filter.status() != null) {
            clauses.add(column(qualifier, "status") + " = " + sqlString(filter.status()));
        }
        if (includeAggregateFilters && filter.errorCode() != null) {
            clauses.add(column(qualifier, "error_code") + " = " + sqlString(filter.errorCode()));
        }
        return String.join(" AND ", clauses);
    }

    private String column(String qualifier, String name) {
        return qualifier == null ? name : qualifier + "." + name;
    }

    private String searchHaving(AnalyticsFilter filter, String decodedCursor) {
        List<String> clauses = new ArrayList<>();
        if (filter.status() != null) {
            clauses.add("status = " + sqlString(filter.status()));
        }
        if (filter.errorCode() != null) {
            clauses.add("error_code = " + sqlString(filter.errorCode()));
        }
        if (decodedCursor != null) {
            String[] parts = decodedCursor.split("\\|", 2);
            if (parts.length == 2) {
                clauses.add("(completed_at < parseDateTime64BestEffort(" + sqlString(parts[0]) + ") OR (completed_at = parseDateTime64BestEffort(" + sqlString(parts[0]) + ") AND request_id < " + sqlString(parts[1]) + "))");
            }
        }
        return clauses.isEmpty() ? "" : "HAVING " + String.join(" AND ", clauses);
    }

    private String sqlString(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    private TotalsRow toTotalsRow(JsonNode node) {
        return new TotalsRow(
                longValue(node, "request_count"),
                longValue(node, "success_count"),
                longValue(node, "failed_count"),
                longValue(node, "cancelled_count"),
                longValue(node, "input_tokens"),
                longValue(node, "output_tokens"),
                longValue(node, "total_tokens"),
                doubleValue(node, "average_duration_ms"),
                doubleValue(node, "p95_duration_ms"));
    }

    private InferenceTimeSeriesPoint toTimeSeriesPoint(JsonNode node) {
        return new InferenceTimeSeriesPoint(
                instantValue(node, "bucket"),
                longValue(node, "request_count"),
                longValue(node, "success_count"),
                longValue(node, "failed_count"),
                longValue(node, "cancelled_count"),
                doubleValue(node, "average_duration_ms"),
                doubleValue(node, "p95_duration_ms"),
                longValue(node, "total_tokens"));
    }

    private DimensionBreakdown toDimensionBreakdown(JsonNode node, long totalRequests) {
        long requestCount = longValue(node, "request_count");
        return new DimensionBreakdown(
                textValue(node, "name"),
                requestCount,
                totalRequests == 0 ? 0 : (double) requestCount / totalRequests,
                doubleValue(node, "average_duration_ms"),
                longValue(node, "total_tokens"),
                0);
    }

    private ErrorBreakdown toErrorBreakdown(JsonNode node) {
        return new ErrorBreakdown(
                textValue(node, "error_code"),
                nullableText(node, "failure_stage"),
                nullableText(node, "provider_error_code"),
                longValue(node, "request_count"),
                longValue(node, "retryable_count"));
    }

    private InferenceRequestRow toRequestRow(JsonNode node) {
        return new InferenceRequestRow(
                UUID.fromString(textValue(node, "request_id")),
                UUID.fromString(textValue(node, "conversation_id")),
                textValue(node, "provider"),
                textValue(node, "model"),
                textValue(node, "status"),
                instantValue(node, "started_at"),
                instantValue(node, "completed_at"),
                longValue(node, "duration_ms"),
                longValue(node, "input_tokens"),
                longValue(node, "output_tokens"),
                longValue(node, "total_tokens"),
                0,
                nullableText(node, "error_code"),
                nullableText(node, "failure_stage"),
                nullableText(node, "correlation_id"),
                nullableText(node, "traceparent"),
                "");
    }

    private LifecycleRow toLifecycleRow(JsonNode node) {
        return new LifecycleRow(
                UUID.fromString(textValue(node, "event_id")),
                textValue(node, "event_name"),
                textValue(node, "tenant_id"),
                textValue(node, "project_id"),
                UUID.fromString(textValue(node, "request_id")),
                UUID.fromString(textValue(node, "conversation_id")),
                textValue(node, "provider"),
                textValue(node, "model"),
                textValue(node, "status"),
                instantValue(node, "occurred_at"),
                nullableLong(node, "duration_ms"),
                nullableInt(node, "input_tokens"),
                nullableInt(node, "output_tokens"),
                nullableInt(node, "total_tokens"),
                nullableText(node, "failure_stage"),
                nullableText(node, "error_code"),
                nullableText(node, "provider_error_code"),
                nullableBoolean(node, "retryable"),
                nullableText(node, "cancellation_reason"),
                nullableBoolean(node, "provider_cancellation_attempted"),
                nullableBoolean(node, "provider_cancellation_succeeded"),
                nullableText(node, "correlation_id"),
                nullableText(node, "traceparent"));
    }

    private InferenceRequestDetail toRequestDetail(AnalyticsFilter filter, List<LifecycleRow> rows) {
        List<LifecycleRow> ordered = rows.stream()
                .sorted(Comparator.comparing(LifecycleRow::occurredAt))
                .toList();
        LifecycleRow first = ordered.get(0);
        LifecycleRow latest = ordered.get(ordered.size() - 1);
        long durationMs = ordered.stream().map(LifecycleRow::durationMs).filter(value -> value != null).mapToLong(Long::longValue).max().orElse(0);
        long inputTokens = ordered.stream().map(LifecycleRow::inputTokens).filter(value -> value != null).mapToLong(Integer::longValue).max().orElse(0);
        long outputTokens = ordered.stream().map(LifecycleRow::outputTokens).filter(value -> value != null).mapToLong(Integer::longValue).max().orElse(0);
        long totalTokens = ordered.stream().map(LifecycleRow::totalTokens).filter(value -> value != null).mapToLong(Integer::longValue).max().orElse(0);
        return new InferenceRequestDetail(
                first.requestId(),
                first.conversationId(),
                filter.tenantId(),
                filter.projectId(),
                first.provider(),
                first.model(),
                latest.status(),
                first.occurredAt(),
                latest.occurredAt(),
                durationMs,
                inputTokens,
                outputTokens,
                totalTokens,
                0,
                latest.errorCode(),
                latest.failureStage(),
                latest.providerErrorCode(),
                latest.retryable(),
                latest.cancellationReason(),
                latest.providerCancellationAttempted(),
                latest.providerCancellationSucceeded(),
                first.correlationId(),
                first.traceparent(),
                ordered.stream().map(row -> new InferenceLifecycleEvent(
                        row.eventId(),
                        row.eventName(),
                        row.occurredAt(),
                        row.status(),
                        row.durationMs(),
                        row.inputTokens(),
                        row.outputTokens(),
                        row.totalTokens(),
                        row.errorCode(),
                        row.failureStage(),
                        row.cancellationReason())).toList());
    }

    private String textValue(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText();
    }

    private String nullableText(JsonNode node, String field) {
        String value = textValue(node, field);
        return value == null || value.isBlank() ? null : value;
    }

    private long longValue(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? 0 : value.asLong();
    }

    private Long nullableLong(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asLong();
    }

    private Integer nullableInt(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asInt();
    }

    private Boolean nullableBoolean(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asBoolean();
    }

    private double doubleValue(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? 0 : value.asDouble();
    }

    private Instant instantValue(JsonNode node, String field) {
        String value = textValue(node, field);
        if (value.isBlank()) {
            return Instant.EPOCH;
        }
        if (value.contains(" ") && !value.contains("T")) {
            value = value.replace(" ", "T") + "Z";
        }
        return Instant.parse(value);
    }

    private record TotalsRow(
            long requestCount,
            long successCount,
            long failedCount,
            long cancelledCount,
            long inputTokens,
            long outputTokens,
            long totalTokens,
            double averageDurationMs,
            double p95DurationMs
    ) {
        TotalsRow() {
            this(0, 0, 0, 0, 0, 0, 0, 0, 0);
        }
    }

    private record LifecycleRow(
            UUID eventId,
            String eventName,
            String tenantId,
            String projectId,
            UUID requestId,
            UUID conversationId,
            String provider,
            String model,
            String status,
            Instant occurredAt,
            Long durationMs,
            Integer inputTokens,
            Integer outputTokens,
            Integer totalTokens,
            String failureStage,
            String errorCode,
            String providerErrorCode,
            Boolean retryable,
            String cancellationReason,
            Boolean providerCancellationAttempted,
            Boolean providerCancellationSucceeded,
            String correlationId,
            String traceparent
    ) {
    }
}
