package com.llmobservability.platform.ingestionworker.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LifecycleEventParserTest {

    @Test
    void parsesLifecycleEventEnvelope() {
        ObjectMapper objectMapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .build();
        LifecycleEventParser parser = new LifecycleEventParser(objectMapper);

        var event = parser.parse("""
                {
                  "eventId": "00000000-0000-0000-0000-000000000001",
                  "eventName": "inference.requested",
                  "schemaVersion": "1.0.0",
                  "occurredAt": "2026-05-23T00:00:00Z",
                  "producer": "inference-gateway",
                  "tenantId": "tenant-a",
                  "projectId": "project-a",
                  "correlationId": "request-1",
                  "traceparent": "trace-1",
                  "idempotencyKey": "request-1:inference.requested",
                  "payload": {
                    "requestId": "request-1",
                    "conversationId": "conversation-1",
                    "provider": "gemini",
                    "model": "gemini-1.5-flash",
                    "status": "ACCEPTED",
                    "streaming": true,
                    "inputMessageCount": 1,
                    "inputContentHash": "abc"
                  }
                }
                """);

        assertThat(event.eventName()).isEqualTo("inference.requested");
        assertThat(event.dedupeKey()).isEqualTo("request-1:inference.requested");
        assertThat(event.payload()).containsEntry("requestId", "request-1");
    }
}
