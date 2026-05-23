package com.llmobservability.platform.inferencegateway.application.port.out;

import com.llmobservability.platform.inferencegateway.application.service.context.ContextEvidence;
import reactor.core.publisher.Mono;

import java.util.List;

public interface MarketDataPort {
    Mono<List<ContextEvidence>> lookup(String query);
}
