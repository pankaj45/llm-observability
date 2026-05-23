package com.llmobservability.platform.inferencegateway.application.port.out;

import com.llmobservability.platform.inferencegateway.application.service.context.ContextToolInvocation;
import reactor.core.publisher.Mono;

public interface ContextToolInvocationRepository {
    Mono<Void> save(ContextToolInvocation invocation);
}
