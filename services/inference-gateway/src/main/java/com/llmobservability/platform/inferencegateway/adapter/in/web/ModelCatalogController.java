package com.llmobservability.platform.inferencegateway.adapter.in.web;

import com.llmobservability.platform.inferencegateway.application.port.in.InferenceGatewayUseCase;
import com.llmobservability.platform.inferencegateway.application.port.in.ModelCatalogResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/catalog")
class ModelCatalogController {

    private final InferenceGatewayUseCase useCase;

    ModelCatalogController(InferenceGatewayUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping("/models")
    Mono<ModelCatalogResponse> listModels() {
        return useCase.listModels()
                .collectList()
                .map(this::toResponse);
    }

    private ModelCatalogResponse toResponse(List<ModelCatalogResult> results) {
        // Group models by provider
        Map<String, ModelCatalogResponse.ProviderResponse> providers = new LinkedHashMap<>();

        for (ModelCatalogResult result : results) {
            ModelCatalogResponse.ProviderResponse provider = providers.computeIfAbsent(
                    result.providerKey(),
                    k -> new ModelCatalogResponse.ProviderResponse(
                            result.providerKey(),
                            result.providerDisplayName(),
                            new ArrayList<>()
                    )
            );

            provider.models().add(new ModelCatalogResponse.ModelResponse(
                    result.modelKey(),
                    result.modelDisplayName(),
                    result.contextWindowTokens(),
                    result.maxOutputTokens(),
                    result.supportsStreaming(),
                    result.supportsCancellation()
            ));
        }

        return new ModelCatalogResponse(new ArrayList<>(providers.values()));
    }
}
