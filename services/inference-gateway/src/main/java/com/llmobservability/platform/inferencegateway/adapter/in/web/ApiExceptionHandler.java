package com.llmobservability.platform.inferencegateway.adapter.in.web;

import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.llmobservability.platform.inferencegateway.application.service.ApplicationException;
import com.llmobservability.platform.inferencegateway.domain.model.ErrorCode;
import org.slf4j.MDC;
import org.springframework.core.codec.DecodingException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebInputException;

import java.util.List;
import java.util.Map;

@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(ApplicationException.class)
    ResponseEntity<ErrorEnvelope> application(ApplicationException exception) {
        return ResponseEntity.status(statusFor(exception.errorCode()))
                .body(ErrorEnvelope.of(exception.errorCode().code(), exception.getMessage(), List.of()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorEnvelope> validation(MethodArgumentNotValidException exception) {
        List<Map<String, Object>> details = exception.getBindingResult().getFieldErrors().stream()
                .map(this::fieldDetail)
                .toList();
        return ResponseEntity.badRequest()
                .body(ErrorEnvelope.of(ErrorCode.VALIDATION_INVALID_REQUEST.code(), "Request validation failed", details));
    }

    @ExceptionHandler(WebExchangeBindException.class)
    ResponseEntity<ErrorEnvelope> webfluxValidation(WebExchangeBindException exception) {
        List<Map<String, Object>> details = exception.getFieldErrors().stream()
                .map(this::fieldDetail)
                .toList();
        return ResponseEntity.badRequest()
                .body(ErrorEnvelope.of(ErrorCode.VALIDATION_INVALID_REQUEST.code(), "Request validation failed", details));
    }

    @ExceptionHandler(ServerWebInputException.class)
    ResponseEntity<ErrorEnvelope> input(ServerWebInputException exception) {
        String message = "Request body is invalid";
        Throwable cause = exception.getCause();
        if (cause instanceof DecodingException decodingException
                && decodingException.getCause() instanceof UnrecognizedPropertyException unrecognized) {
            message = "Unknown request property '" + unrecognized.getPropertyName() + "'";
        }
        return ResponseEntity.badRequest()
                .body(ErrorEnvelope.of(ErrorCode.VALIDATION_INVALID_REQUEST.code(), message, List.of()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ErrorEnvelope> illegalArgument(IllegalArgumentException exception) {
        return ResponseEntity.badRequest()
                .body(ErrorEnvelope.of(ErrorCode.VALIDATION_INVALID_REQUEST.code(), exception.getMessage(), List.of()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ErrorEnvelope> accessDenied(AccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorEnvelope.of("AUTHORIZATION_DENIED", "Authorization denied", List.of()));
    }

    @ExceptionHandler(Throwable.class)
    ResponseEntity<ErrorEnvelope> fallback(Throwable exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorEnvelope.of(ErrorCode.INTERNAL_PROVIDER_ERROR.code(), "Internal server error", List.of()));
    }

    private HttpStatus statusFor(ErrorCode errorCode) {
        return switch (errorCode) {
            case VALIDATION_INVALID_REQUEST, PROVIDER_UNSUPPORTED, CONVERSATION_NOT_FOUND -> HttpStatus.BAD_REQUEST;
            case PROVIDER_RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
            case PROVIDER_TIMEOUT, PROVIDER_UNAVAILABLE -> HttpStatus.BAD_GATEWAY;
            case STREAM_CANCELLED -> HttpStatus.CONFLICT;
            case INTERNAL_PERSISTENCE_ERROR, INTERNAL_EVENT_PUBLISH_ERROR, INTERNAL_PROVIDER_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    private Map<String, Object> fieldDetail(FieldError fieldError) {
        return Map.of(
                "field", fieldError.getField(),
                "message", fieldError.getDefaultMessage() == null ? "invalid" : fieldError.getDefaultMessage());
    }

    record ErrorEnvelope(ErrorBody error) {
        static ErrorEnvelope of(String code, String message, List<Map<String, Object>> details) {
            String traceId = MDC.get("traceId");
            return new ErrorEnvelope(new ErrorBody(code, message, details, traceId == null ? "" : traceId));
        }
    }

    record ErrorBody(String code, String message, List<Map<String, Object>> details, String traceId) {
    }
}
