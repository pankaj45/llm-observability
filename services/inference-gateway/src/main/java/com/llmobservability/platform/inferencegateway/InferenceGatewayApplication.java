package com.llmobservability.platform.inferencegateway;

import com.llmobservability.platform.inferencegateway.config.SecurityProperties;
import com.llmobservability.platform.inferencegateway.config.ContextOrchestratorProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({SecurityProperties.class, ContextOrchestratorProperties.class})
public class InferenceGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(InferenceGatewayApplication.class, args);
    }
}
