package com.llmobservability.platform.ingestionworker.domain.model;

public record IngestionResult(
        String eventId,
        String eventName,
        boolean duplicate
) {
    public static IngestionResult processed(LifecycleEvent event) {
        return new IngestionResult(event.eventId(), event.eventName(), false);
    }

    public static IngestionResult duplicate(LifecycleEvent event) {
        return new IngestionResult(event.eventId(), event.eventName(), true);
    }
}
