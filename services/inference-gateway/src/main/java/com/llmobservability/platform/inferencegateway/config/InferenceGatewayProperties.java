package com.llmobservability.platform.inferencegateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "llm-observability.inference")
public record InferenceGatewayProperties(
        Duration streamHeartbeat,
        Duration activeStreamTtl
) {
    public InferenceGatewayProperties {
        if (streamHeartbeat == null) {
            streamHeartbeat = Duration.ofSeconds(15);
        }
        if (activeStreamTtl == null) {
            activeStreamTtl = Duration.ofMinutes(10);
        }
    }
}

