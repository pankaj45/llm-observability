package com.llmobservability.platform.inferencegateway.domain.model;

public enum StreamEventType {
    REQUEST_ACCEPTED("request.accepted"),
    TOKEN_DELTA("token.delta"),
    MESSAGE_DELTA("message.delta"),
    USAGE_DELTA("usage.delta"),
    REQUEST_COMPLETED("request.completed"),
    REQUEST_CANCELLED("request.cancelled"),
    REQUEST_FAILED("request.failed"),
    HEARTBEAT("heartbeat");

    private final String wireName;

    StreamEventType(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }
}

