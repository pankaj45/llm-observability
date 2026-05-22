package com.llmobservability.platform.ingestionworker.adapter.in.kafka;

import com.llmobservability.platform.ingestionworker.application.service.InferenceLifecycleIngestionService;
import com.llmobservability.platform.ingestionworker.application.service.LifecycleEventParser;
import com.llmobservability.platform.ingestionworker.domain.model.EventSource;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "llm-observability.ingestion.kafka", name = "enabled", havingValue = "true")
class InferenceLifecycleKafkaConsumer {
    private final LifecycleEventParser parser;
    private final InferenceLifecycleIngestionService ingestionService;

    InferenceLifecycleKafkaConsumer(LifecycleEventParser parser, InferenceLifecycleIngestionService ingestionService) {
        this.parser = parser;
        this.ingestionService = ingestionService;
    }

    @KafkaListener(
            topics = "${llm-observability.ingestion.kafka.lifecycle-topic}",
            groupId = "${llm-observability.ingestion.kafka.group-id}"
    )
    void consume(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        ingestionService.ingest(
                        parser.parse(record.value()),
                        new EventSource(record.topic(), record.partition(), record.offset()))
                .doOnSuccess(ignored -> acknowledgment.acknowledge())
                .block();
    }
}
