package com.llmobservability.platform.inferencegateway.adapter.out.provider;

import com.llmobservability.platform.inferencegateway.application.port.out.ProviderClient;
import com.llmobservability.platform.inferencegateway.application.port.out.ProviderClientRegistry;
import com.llmobservability.platform.inferencegateway.application.service.ApplicationException;
import com.llmobservability.platform.inferencegateway.domain.model.ErrorCode;
import com.llmobservability.platform.inferencegateway.domain.model.FailureStage;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
class ProviderClientRegistryAdapter implements ProviderClientRegistry {
    private final Map<String, ProviderClient> clients;

    ProviderClientRegistryAdapter(List<ProviderClient> clients) {
        this.clients = clients.stream().collect(Collectors.toUnmodifiableMap(ProviderClient::providerKey, Function.identity()));
    }

    @Override
    public Mono<ProviderClient> get(String providerKey) {
        ProviderClient client = clients.get(providerKey);
        if (client == null) {
            return Mono.error(new ApplicationException(
                    ErrorCode.PROVIDER_UNSUPPORTED,
                    FailureStage.VALIDATION,
                    "Provider is not supported: " + providerKey));
        }
        return Mono.just(client);
    }
}
