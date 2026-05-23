package com.llmobservability.platform.analyticsquery.application.port.in;

import com.llmobservability.platform.analyticsquery.domain.model.InferenceRequestDetail;
import com.llmobservability.platform.analyticsquery.domain.model.InferenceRequestRow;
import com.llmobservability.platform.analyticsquery.domain.model.InferenceSummary;
import reactor.core.publisher.Mono;

public interface AnalyticsQueryUseCase {
    Mono<InferenceSummary> summary(GetInferenceSummaryQuery query);

    Mono<PagedResult<InferenceRequestRow>> searchRequests(SearchInferenceRequestsQuery query);

    Mono<InferenceRequestDetail> requestDetail(GetInferenceRequestDetailQuery query);
}
