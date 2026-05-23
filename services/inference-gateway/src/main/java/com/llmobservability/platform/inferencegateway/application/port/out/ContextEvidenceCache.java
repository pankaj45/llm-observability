package com.llmobservability.platform.inferencegateway.application.port.out;

import com.llmobservability.platform.inferencegateway.application.service.context.ContextEvidence;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

public interface ContextEvidenceCache {
    Mono<List<ContextEvidence>> get(String key);

    Mono<Void> put(String key, List<ContextEvidence> evidence, Duration ttl);
}
