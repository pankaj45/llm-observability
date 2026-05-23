package com.llmobservability.platform.analyticsquery.application.port.out;

import com.llmobservability.platform.analyticsquery.domain.model.AnalyticsFilter;
import com.llmobservability.platform.analyticsquery.domain.model.InferenceRequestDetail;
import com.llmobservability.platform.analyticsquery.domain.model.InferenceRequestRow;
import com.llmobservability.platform.analyticsquery.domain.model.InferenceSummary;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface InferenceAnalyticsRepository {
    Mono<InferenceSummary> summary(AnalyticsFilter filter);

    Flux<InferenceRequestRow> searchRequests(AnalyticsFilter filter, String decodedCursor, int limit);

    Mono<InferenceRequestDetail> findRequestDetail(AnalyticsFilter filter, UUID requestId);
}
