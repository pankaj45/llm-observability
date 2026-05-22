package com.llmobservability.platform.inferencegateway.application.port.out;

import com.llmobservability.platform.inferencegateway.domain.model.ModelCatalogEntry;
import reactor.core.publisher.Mono;

public interface ModelCatalogRepository {
    Mono<ModelCatalogEntry> findEnabledModel(String providerKey, String modelKey);
}

