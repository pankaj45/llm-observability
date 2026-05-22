package com.llmobservability.platform.ingestionworker.application.service;

import com.llmobservability.platform.ingestionworker.application.port.out.AnalyticsEventSink;
import com.llmobservability.platform.ingestionworker.application.port.out.ProcessedEventRepository;
import com.llmobservability.platform.ingestionworker.domain.model.AnalyticsLifecycleFact;
import com.llmobservability.platform.ingestionworker.domain.model.EventSource;
import com.llmobservability.platform.ingestionworker.domain.model.IngestionResult;
import com.llmobservability.platform.ingestionworker.domain.model.LifecycleEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;

@Service
public class InferenceLifecycleIngestionService {
    private final ProcessedEventRepository processedEventRepository;
    private final AnalyticsEventSink analyticsEventSink;
    private final MeterRegistry meterRegistry;

    public InferenceLifecycleIngestionService(
            ProcessedEventRepository processedEventRepository,
            AnalyticsEventSink analyticsEventSink,
            MeterRegistry meterRegistry
    ) {
        this.processedEventRepository = processedEventRepository;
        this.analyticsEventSink = analyticsEventSink;
        this.meterRegistry = meterRegistry;
    }

    public Mono<IngestionResult> ingest(LifecycleEvent event, EventSource source) {
        return processedEventRepository.tryStart(event, source)
                .flatMap(started -> {
                    if (!started) {
                        Counter.builder("ingestion_lifecycle_events_duplicates_total")
                                .description("Duplicate lifecycle events skipped by ingestion")
                                .tag("event", event.eventName())
                                .register(meterRegistry)
                                .increment();
                        return Mono.just(IngestionResult.duplicate(event));
                    }
                    return analyticsEventSink.write(toFact(event))
                            .then(processedEventRepository.markProcessed(event))
                            .thenReturn(IngestionResult.processed(event))
                            .doOnSuccess(result -> Counter.builder("ingestion_lifecycle_events_processed_total")
                                    .description("Lifecycle events processed by ingestion")
                                    .tag("event", event.eventName())
                                    .register(meterRegistry)
                                    .increment())
                            .onErrorResume(error -> processedEventRepository.markFailed(event, error)
                                    .then(Mono.error(error)));
                });
    }

    private AnalyticsLifecycleFact toFact(LifecycleEvent event) {
        Map<String, Object> payload = event.payload() == null ? Map.of() : event.payload();
        return new AnalyticsLifecycleFact(
                event.eventId(),
                event.eventName(),
                event.schemaVersion(),
                event.occurredAt(),
                event.producer(),
                event.tenantId(),
                event.projectId(),
                event.correlationId(),
                event.traceparent(),
                string(payload, "requestId"),
                string(payload, "conversationId"),
                string(payload, "provider"),
                string(payload, "model"),
                string(payload, "status"),
                integer(payload, "inputMessageCount"),
                string(payload, "inputContentHash"),
                integer(payload, "inputTokens"),
                integer(payload, "outputTokens"),
                integer(payload, "totalTokens"),
                longValue(payload, "durationMs"),
                string(payload, "failureStage"),
                string(payload, "errorCode"),
                string(payload, "providerErrorCode"),
                bool(payload, "retryable"),
                string(payload, "reason"),
                bool(payload, "providerCancellationAttempted"),
                bool(payload, "providerCancellationSucceeded"));
    }

    private String string(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value == null ? null : value.toString();
    }

    private Integer integer(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Integer.parseInt(text);
        }
        return null;
    }

    private Long longValue(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Long.parseLong(text);
        }
        return null;
    }

    private Boolean bool(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof String text && !text.isBlank()) {
            return Boolean.parseBoolean(text);
        }
        return null;
    }
}
