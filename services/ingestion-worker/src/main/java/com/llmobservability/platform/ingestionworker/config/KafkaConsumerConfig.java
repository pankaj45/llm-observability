package com.llmobservability.platform.ingestionworker.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;

@Configuration
@EnableKafka
@ConditionalOnProperty(prefix = "llm-observability.ingestion.kafka", name = "enabled", havingValue = "true")
class KafkaConsumerConfig {
}
