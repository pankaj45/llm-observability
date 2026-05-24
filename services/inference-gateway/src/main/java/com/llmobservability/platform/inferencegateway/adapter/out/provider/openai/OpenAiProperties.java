package com.llmobservability.platform.inferencegateway.adapter.out.provider.openai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "llm-observability.openai")
public record OpenAiProperties(
        String apiKey,
        String baseUrl,
        String organization,
        String project,
        String defaultModel
) {
    public OpenAiProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://api.openai.com";
        }
        if (defaultModel == null || defaultModel.isBlank()) {
            defaultModel = "gpt-5.5";
        }
    }
}
