package com.llmobservability.platform.inferencegateway.application.service;

import com.llmobservability.platform.inferencegateway.domain.model.ErrorCode;
import com.llmobservability.platform.inferencegateway.domain.model.FailureStage;

public class ApplicationException extends RuntimeException {
    private final ErrorCode errorCode;
    private final FailureStage failureStage;
    private final String providerErrorCode;
    private final boolean retryable;

    public ApplicationException(ErrorCode errorCode, FailureStage failureStage, String message) {
        this(errorCode, failureStage, null, message, false, null);
    }

    public ApplicationException(ErrorCode errorCode, FailureStage failureStage, String providerErrorCode, String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.failureStage = failureStage;
        this.providerErrorCode = providerErrorCode;
        this.retryable = retryable;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public FailureStage failureStage() {
        return failureStage;
    }

    public String providerErrorCode() {
        return providerErrorCode;
    }

    public boolean retryable() {
        return retryable;
    }
}

