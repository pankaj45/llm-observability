package com.llmobservability.platform.inferencegateway.application.service.context;

import com.llmobservability.platform.inferencegateway.domain.model.StreamEventType;

import java.util.Map;

public record ContextProgressEvent(StreamEventType type, Map<String, Object> data) {
}
