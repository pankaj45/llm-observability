package com.llmobservability.platform.analyticsquery.domain.model;

import java.time.Instant;

public record AnalyticsWindow(Instant from, Instant to) {
}
