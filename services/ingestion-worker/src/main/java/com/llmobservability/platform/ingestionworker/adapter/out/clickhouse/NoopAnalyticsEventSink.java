package com.llmobservability.platform.ingestionworker.adapter.out.clickhouse;

import com.llmobservability.platform.ingestionworker.application.port.out.AnalyticsEventSink;
import com.llmobservability.platform.ingestionworker.domain.model.AnalyticsLifecycleFact;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@ConditionalOnProperty(prefix = "llm-observability.ingestion.clickhouse", name = "enabled", havingValue = "false", matchIfMissing = true)
class NoopAnalyticsEventSink implements AnalyticsEventSink {
    @Override
    public Mono<Void> write(AnalyticsLifecycleFact fact) {
        return Mono.empty();
    }
}
