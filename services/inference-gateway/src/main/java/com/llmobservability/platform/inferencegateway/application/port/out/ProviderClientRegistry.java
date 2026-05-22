package com.llmobservability.platform.inferencegateway.application.port.out;

import reactor.core.publisher.Mono;

public interface ProviderClientRegistry {
    Mono<ProviderClient> get(String providerKey);
}

