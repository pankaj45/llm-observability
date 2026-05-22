package com.llmobservability.platform.inferencegateway.adapter.out.provider.gemini;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "llm-observability.gemini")
public record GeminiProperties(
        String apiKey,
        String baseUrl,
        String defaultModel
) {
    public GeminiProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://generativelanguage.googleapis.com";
        }
        if (defaultModel == null || defaultModel.isBlank()) {
            defaultModel = "gemini-1.5-flash";
        }
    }
}
