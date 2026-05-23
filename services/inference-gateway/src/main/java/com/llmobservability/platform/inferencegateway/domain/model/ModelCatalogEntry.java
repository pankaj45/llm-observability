package com.llmobservability.platform.inferencegateway.domain.model;

import java.util.UUID;

public record ModelCatalogEntry(
        UUID providerId,
        UUID modelId,
        String providerKey,
        String providerDisplayName,
        String modelKey,
        String modelDisplayName,
        int contextWindowTokens,
        int maxOutputTokens,
        boolean providerSupportsStreaming,
        boolean providerSupportsCancellation
) {
}


