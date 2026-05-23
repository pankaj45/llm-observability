package com.llmobservability.platform.inferencegateway.application.service.context;

import com.llmobservability.platform.inferencegateway.config.ContextOrchestratorProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Component
public class RuntimeContextPolicy {
    private final ContextOrchestratorProperties properties;
    private final Clock clock;

    @Autowired
    public RuntimeContextPolicy(ContextOrchestratorProperties properties) {
        this(properties, Clock.systemUTC());
    }

    RuntimeContextPolicy(ContextOrchestratorProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    String instruction() {
        ZoneId zoneId = ZoneId.of(properties.getTimezone());
        ZonedDateTime now = ZonedDateTime.now(clock).withZoneSameInstant(zoneId);
        return """
                Current date: %s
                Current time: %s
                Current timezone: %s

                For current, recent, volatile, or high-stakes facts, use the provided tool evidence before answering.
                If required tools are unavailable or fail, state that current verification was unavailable and the answer may be stale or incomplete.
                Do not invent source URLs, prices, dates, regulations, software versions, or news.
                """.formatted(now.toLocalDate(), now.toOffsetDateTime(), zoneId);
    }
}
