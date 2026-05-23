package com.llmobservability.platform.inferencegateway.adapter.in.web;

import java.util.List;

public record ModelCatalogResponse(
        List<ProviderResponse> providers
) {
    public record ProviderResponse(
            String id,
            String name,
            List<ModelResponse> models
    ) {}

    public record ModelResponse(
            String id,
            String name,
            int contextWindowTokens,
            int maxOutputTokens,
            boolean supportsStreaming,
            boolean supportsCancellation
    ) {}
}
