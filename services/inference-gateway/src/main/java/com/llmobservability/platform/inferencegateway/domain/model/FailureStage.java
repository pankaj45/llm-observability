package com.llmobservability.platform.inferencegateway.domain.model;

public enum FailureStage {
    VALIDATION,
    PERSISTENCE,
    PROVIDER,
    STREAMING,
    EVENT_PUBLISH
}

