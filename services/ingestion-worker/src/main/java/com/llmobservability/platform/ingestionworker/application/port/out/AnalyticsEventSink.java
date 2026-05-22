package com.llmobservability.platform.ingestionworker.application.port.out;

import com.llmobservability.platform.ingestionworker.domain.model.AnalyticsLifecycleFact;
import reactor.core.publisher.Mono;

public interface AnalyticsEventSink {
    Mono<Void> write(AnalyticsLifecycleFact fact);
}
