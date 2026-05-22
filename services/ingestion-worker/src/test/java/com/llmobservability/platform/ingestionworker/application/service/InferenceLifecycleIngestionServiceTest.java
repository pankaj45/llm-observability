package com.llmobservability.platform.ingestionworker.application.service;

import com.llmobservability.platform.ingestionworker.application.port.out.AnalyticsEventSink;
import com.llmobservability.platform.ingestionworker.application.port.out.ProcessedEventRepository;
import com.llmobservability.platform.ingestionworker.domain.model.AnalyticsLifecycleFact;
import com.llmobservability.platform.ingestionworker.domain.model.EventSource;
import com.llmobservability.platform.ingestionworker.domain.model.LifecycleEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InferenceLifecycleIngestionServiceTest {

    @Test
    void writesAnalyticsFactAndMarksEventProcessed() {
        TestProcessedEventRepository processedEvents = new TestProcessedEventRepository();
        TestAnalyticsEventSink analytics = new TestAnalyticsEventSink();
        InferenceLifecycleIngestionService service = new InferenceLifecycleIngestionService(
                processedEvents,
                analytics,
                new SimpleMeterRegistry());

        LifecycleEvent event = completedEvent("idem-1");

        StepVerifier.create(service.ingest(event, EventSource.unknown()))
                .assertNext(result -> {
                    assertThat(result.duplicate()).isFalse();
                    assertThat(result.eventName()).isEqualTo("inference.completed");
                })
                .verifyComplete();

        assertThat(processedEvents.processed).contains(event.dedupeKey());
        assertThat(analytics.facts).singleElement()
                .satisfies(fact -> {
                    assertThat(fact.requestId()).isEqualTo("request-1");
                    assertThat(fact.conversationId()).isEqualTo("conversation-1");
                    assertThat(fact.inputTokens()).isEqualTo(10);
                    assertThat(fact.outputTokens()).isEqualTo(5);
                    assertThat(fact.durationMs()).isEqualTo(250L);
                });
    }

    @Test
    void skipsDuplicateEventsWithoutWritingAnalytics() {
        TestProcessedEventRepository processedEvents = new TestProcessedEventRepository();
        TestAnalyticsEventSink analytics = new TestAnalyticsEventSink();
        InferenceLifecycleIngestionService service = new InferenceLifecycleIngestionService(
                processedEvents,
                analytics,
                new SimpleMeterRegistry());
        LifecycleEvent event = completedEvent("idem-duplicate");

        StepVerifier.create(service.ingest(event, EventSource.unknown()))
                .expectNextMatches(result -> !result.duplicate())
                .verifyComplete();
        StepVerifier.create(service.ingest(event, EventSource.unknown()))
                .expectNextMatches(result -> result.duplicate())
                .verifyComplete();

        assertThat(analytics.facts).hasSize(1);
    }

    @Test
    void marksFailedWhenAnalyticsWriteFails() {
        TestProcessedEventRepository processedEvents = new TestProcessedEventRepository();
        TestAnalyticsEventSink analytics = new TestAnalyticsEventSink();
        analytics.failWrites = true;
        InferenceLifecycleIngestionService service = new InferenceLifecycleIngestionService(
                processedEvents,
                analytics,
                new SimpleMeterRegistry());
        LifecycleEvent event = completedEvent("idem-failure");

        StepVerifier.create(service.ingest(event, EventSource.unknown()))
                .expectErrorMatches(error -> error.getMessage().equals("clickhouse unavailable"))
                .verify();

        assertThat(processedEvents.failed).contains(event.dedupeKey());
    }

    @Test
    void retriesEventsThatPreviouslyFailed() {
        TestProcessedEventRepository processedEvents = new TestProcessedEventRepository();
        TestAnalyticsEventSink analytics = new TestAnalyticsEventSink();
        InferenceLifecycleIngestionService service = new InferenceLifecycleIngestionService(
                processedEvents,
                analytics,
                new SimpleMeterRegistry());
        LifecycleEvent event = completedEvent("idem-retry");
        processedEvents.failed.add(event.dedupeKey());

        StepVerifier.create(service.ingest(event, EventSource.unknown()))
                .expectNextMatches(result -> !result.duplicate())
                .verifyComplete();

        assertThat(processedEvents.processed).contains(event.dedupeKey());
        assertThat(analytics.facts).hasSize(1);
    }

    private LifecycleEvent completedEvent(String idempotencyKey) {
        return new LifecycleEvent(
                UUID.randomUUID().toString(),
                "inference.completed",
                "1.0.0",
                Instant.parse("2026-05-23T00:00:00Z"),
                "inference-gateway",
                "tenant-a",
                "project-a",
                "request-1",
                "trace-1",
                idempotencyKey,
                Map.of(
                        "requestId", "request-1",
                        "conversationId", "conversation-1",
                        "provider", "gemini",
                        "model", "gemini-1.5-flash",
                        "status", "COMPLETED",
                        "inputTokens", 10,
                        "outputTokens", 5,
                        "totalTokens", 15,
                        "durationMs", 250));
    }

    private static final class TestProcessedEventRepository implements ProcessedEventRepository {
        final Set<String> started = new HashSet<>();
        final Set<String> processed = new HashSet<>();
        final Set<String> failed = new HashSet<>();

        @Override
        public Mono<Boolean> tryStart(LifecycleEvent event, EventSource source) {
            if (failed.remove(event.dedupeKey())) {
                started.add(event.dedupeKey());
                return Mono.just(true);
            }
            return Mono.just(started.add(event.dedupeKey()));
        }

        @Override
        public Mono<Void> markProcessed(LifecycleEvent event) {
            processed.add(event.dedupeKey());
            return Mono.empty();
        }

        @Override
        public Mono<Void> markFailed(LifecycleEvent event, Throwable error) {
            failed.add(event.dedupeKey());
            return Mono.empty();
        }
    }

    private static final class TestAnalyticsEventSink implements AnalyticsEventSink {
        final List<AnalyticsLifecycleFact> facts = new ArrayList<>();
        boolean failWrites;

        @Override
        public Mono<Void> write(AnalyticsLifecycleFact fact) {
            if (failWrites) {
                return Mono.error(new IllegalStateException("clickhouse unavailable"));
            }
            facts.add(fact);
            return Mono.empty();
        }
    }
}
