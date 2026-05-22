package com.llmobservability.platform.ingestionworker.application.port.out;

import com.llmobservability.platform.ingestionworker.domain.model.EventSource;
import com.llmobservability.platform.ingestionworker.domain.model.LifecycleEvent;
import reactor.core.publisher.Mono;

public interface ProcessedEventRepository {
    Mono<Boolean> tryStart(LifecycleEvent event, EventSource source);

    Mono<Void> markProcessed(LifecycleEvent event);

    Mono<Void> markFailed(LifecycleEvent event, Throwable error);
}
