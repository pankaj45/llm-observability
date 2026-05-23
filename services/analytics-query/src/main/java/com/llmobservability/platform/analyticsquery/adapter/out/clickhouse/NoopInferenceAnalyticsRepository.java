package com.llmobservability.platform.analyticsquery.adapter.out.clickhouse;

import com.llmobservability.platform.analyticsquery.application.port.out.InferenceAnalyticsRepository;
import com.llmobservability.platform.analyticsquery.domain.model.AnalyticsFilter;
import com.llmobservability.platform.analyticsquery.domain.model.InferenceRequestDetail;
import com.llmobservability.platform.analyticsquery.domain.model.InferenceRequestRow;
import com.llmobservability.platform.analyticsquery.domain.model.InferenceSummary;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
@ConditionalOnProperty(prefix = "llm-observability.analytics.clickhouse", name = "enabled", havingValue = "false")
class NoopInferenceAnalyticsRepository implements InferenceAnalyticsRepository {
    @Override
    public Mono<InferenceSummary> summary(AnalyticsFilter filter) {
        return Mono.just(InferenceSummary.empty(filter, true));
    }

    @Override
    public Flux<InferenceRequestRow> searchRequests(AnalyticsFilter filter, String decodedCursor, int limit) {
        return Flux.empty();
    }

    @Override
    public Mono<InferenceRequestDetail> findRequestDetail(AnalyticsFilter filter, UUID requestId) {
        return Mono.empty();
    }
}
