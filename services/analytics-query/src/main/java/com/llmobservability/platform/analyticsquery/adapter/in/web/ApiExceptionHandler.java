package com.llmobservability.platform.analyticsquery.adapter.in.web;

import com.llmobservability.platform.analyticsquery.application.service.ApplicationException;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
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
        return ResponseEntity.status(exception.status())
                .body(ErrorEnvelope.of(exception.code(), exception.getMessage(), List.of()));
    }

    @ExceptionHandler(WebExchangeBindException.class)
    ResponseEntity<ErrorEnvelope> validation(WebExchangeBindException exception) {
        List<Map<String, Object>> details = exception.getFieldErrors().stream()
                .map(this::fieldDetail)
                .toList();
        return ResponseEntity.badRequest()
                .body(ErrorEnvelope.of("VALIDATION_INVALID_REQUEST", "Request validation failed", details));
    }

    @ExceptionHandler(ServerWebInputException.class)
    ResponseEntity<ErrorEnvelope> input(ServerWebInputException exception) {
        return ResponseEntity.badRequest()
                .body(ErrorEnvelope.of("VALIDATION_INVALID_REQUEST", "Request parameters are invalid", List.of()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ErrorEnvelope> illegalArgument(IllegalArgumentException exception) {
        return ResponseEntity.badRequest()
                .body(ErrorEnvelope.of("VALIDATION_INVALID_REQUEST", exception.getMessage(), List.of()));
    }

    @ExceptionHandler(Throwable.class)
    ResponseEntity<ErrorEnvelope> fallback(Throwable exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorEnvelope.of("ANALYTICS_QUERY_FAILED", "Internal server error", List.of()));
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
