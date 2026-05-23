package com.llmobservability.platform.analyticsquery.application.service;

import org.springframework.http.HttpStatus;

public class ApplicationException extends RuntimeException {
    private final String code;
    private final HttpStatus status;

    public ApplicationException(String code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }
}
