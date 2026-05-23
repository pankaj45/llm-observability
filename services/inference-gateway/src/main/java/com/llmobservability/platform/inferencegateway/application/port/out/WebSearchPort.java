package com.llmobservability.platform.inferencegateway.application.port.out;

import com.llmobservability.platform.inferencegateway.application.service.context.ContextEvidence;
import reactor.core.publisher.Mono;

import java.util.List;

public interface WebSearchPort {
    Mono<List<ContextEvidence>> search(String query, int maxResults);
}
