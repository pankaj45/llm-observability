package com.llmobservability.platform.inferencegateway.config;

import com.llmobservability.platform.inferencegateway.adapter.out.provider.gemini.GeminiProperties;
import com.llmobservability.platform.inferencegateway.adapter.out.provider.openai.OpenAiProperties;
import com.llmobservability.platform.inferencegateway.application.service.ConversationTitlePolicy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({InferenceGatewayProperties.class, GeminiProperties.class, OpenAiProperties.class})
public class InferenceGatewayConfig {

    @Bean
    ConversationTitlePolicy conversationTitlePolicy() {
        return new ConversationTitlePolicy();
    }
}
