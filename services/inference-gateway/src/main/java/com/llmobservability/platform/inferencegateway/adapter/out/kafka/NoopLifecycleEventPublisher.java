package com.llmobservability.platform.inferencegateway.adapter.out.kafka;

import com.llmobservability.platform.inferencegateway.application.port.out.LifecycleEventPublisher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "llm-observability.kafka", name = "enabled", havingValue = "false", matchIfMissing = true)
class NoopLifecycleEventPublisher implements LifecycleEventPublisher {
    @Override
    public Mono<Void> publish(String eventName, String tenantId, String projectId, String correlationId, String traceparent, String idempotencyKey, Map<String, Object> payload) {
        return Mono.empty();
    }
}
