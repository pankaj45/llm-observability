package com.llmobservability.platform.inferencegateway.adapter.out.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.llmobservability.platform.inferencegateway.application.port.out.LifecycleEventPublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
@ConditionalOnProperty(prefix = "llm-observability.kafka", name = "enabled", havingValue = "true")
class KafkaLifecycleEventPublisher implements LifecycleEventPublisher {
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topic;

    KafkaLifecycleEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${llm-observability.kafka.lifecycle-topic}") String topic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topic = topic;
    }

    @Override
    public Mono<Void> publish(String eventName, String tenantId, String projectId, String correlationId, String traceparent, String idempotencyKey, Map<String, Object> payload) {
        LifecycleEvent event = new LifecycleEvent(
                UUID.randomUUID().toString(),
                eventName,
                "1.0.0",
                Instant.now().toString(),
                "inference-gateway",
                tenantId,
                projectId,
                correlationId,
                traceparent,
                idempotencyKey,
                payload);
        return Mono.fromFuture(kafkaTemplate.send(topic, correlationId, serialize(event)))
                .then();
    }

    private String serialize(LifecycleEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Unable to serialize lifecycle event", e);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record LifecycleEvent(
            String eventId,
            String eventName,
            String schemaVersion,
            String occurredAt,
            String producer,
            String tenantId,
            String projectId,
            String correlationId,
            String traceparent,
            String idempotencyKey,
            Map<String, Object> payload
    ) {
    }
}
