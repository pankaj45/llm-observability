package com.llmobservability.platform.ingestionworker.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.llmobservability.platform.ingestionworker.domain.model.LifecycleEvent;
import org.springframework.stereotype.Component;

@Component
public class LifecycleEventParser {
    private final ObjectMapper objectMapper;

    public LifecycleEventParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public LifecycleEvent parse(String value) {
        try {
            return objectMapper.readValue(value, LifecycleEvent.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Unable to parse lifecycle event", e);
        }
    }
}
