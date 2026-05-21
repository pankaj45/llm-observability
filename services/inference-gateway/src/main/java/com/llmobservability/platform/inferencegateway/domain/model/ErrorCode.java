package com.llmobservability.platform.inferencegateway.domain.model;

public enum ErrorCode {
    VALIDATION_INVALID_REQUEST("validation.invalid_request"),
    CONVERSATION_NOT_FOUND("conversation.not_found"),
    PROVIDER_UNSUPPORTED("provider.unsupported"),
    PROVIDER_TIMEOUT("provider.timeout"),
    PROVIDER_RATE_LIMITED("provider.rate_limited"),
    PROVIDER_UNAVAILABLE("provider.unavailable"),
    STREAM_CANCELLED("stream.cancelled"),
    INTERNAL_PERSISTENCE_ERROR("internal.persistence_error"),
    INTERNAL_EVENT_PUBLISH_ERROR("internal.event_publish_error"),
    INTERNAL_PROVIDER_ERROR("internal.provider_error");

    private final String code;

    ErrorCode(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}

