package com.llmobservability.platform.inferencegateway.application.port.out;

import reactor.core.publisher.Mono;

import java.util.Map;

public interface LifecycleEventPublisher {
    Mono<Void> publish(String eventName, String tenantId, String projectId, String correlationId, String traceparent, String idempotencyKey, Map<String, Object> payload);
}

