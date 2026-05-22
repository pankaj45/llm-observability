package com.llmobservability.platform.ingestionworker.domain.model;

public record EventSource(
        String topic,
        Integer partition,
        Long offset
) {
    public static EventSource unknown() {
        return new EventSource(null, null, null);
    }
}
