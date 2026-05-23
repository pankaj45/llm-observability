package com.llmobservability.platform.inferencegateway.domain.model;

public enum StreamEventType {
    REQUEST_ACCEPTED("request.accepted"),
    TOKEN_DELTA("token.delta"),
    MESSAGE_DELTA("message.delta"),
    USAGE_DELTA("usage.delta"),
    REQUEST_COMPLETED("request.completed"),
    REQUEST_CANCELLED("request.cancelled"),
    REQUEST_FAILED("request.failed"),
    TOOL_PLAN("tool.plan"),
    TOOL_STARTED("tool.started"),
    TOOL_COMPLETED("tool.completed"),
    TOOL_FAILED("tool.failed"),
    SOURCE_AVAILABLE("source.available"),
    HEARTBEAT("heartbeat");

    private final String wireName;

    StreamEventType(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }
}
