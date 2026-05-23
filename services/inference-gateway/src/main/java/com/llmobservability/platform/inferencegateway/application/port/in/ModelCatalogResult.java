package com.llmobservability.platform.inferencegateway.application.port.in;

/**
 * A single enabled model entry returned by the catalog API.
 *
 * @param providerKey            stable identifier for the provider (e.g. "gemini")
 * @param providerDisplayName    human-readable provider name (e.g. "Google Gemini")
 * @param modelKey               stable identifier for the model (e.g. "gemini-2.5-flash")
 * @param modelDisplayName       human-readable model name (e.g. "Gemini 2.5 Flash")
 * @param contextWindowTokens    maximum input context in tokens
 * @param maxOutputTokens        maximum output tokens per request
 * @param supportsStreaming       whether the provider supports SSE streaming for this model
 * @param supportsCancellation   whether the provider supports mid-stream cancellation
 */
public record ModelCatalogResult(
        String providerKey,
        String providerDisplayName,
        String modelKey,
        String modelDisplayName,
        int contextWindowTokens,
        int maxOutputTokens,
        boolean supportsStreaming,
        boolean supportsCancellation
) {
}
