package com.llmobservability.platform.inferencegateway.adapter.in.web;

import com.llmobservability.platform.inferencegateway.application.port.in.CancelInferenceCommand;
import com.llmobservability.platform.inferencegateway.application.port.in.GetInferenceStatusQuery;
import com.llmobservability.platform.inferencegateway.application.port.in.InferenceGatewayUseCase;
import com.llmobservability.platform.inferencegateway.application.port.in.StartInferenceCommand;
import com.llmobservability.platform.inferencegateway.domain.model.MessageRole;
import com.llmobservability.platform.inferencegateway.domain.model.StreamEvent;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/v1/inference")
public class InferenceController {
    private final InferenceGatewayUseCase useCase;
    private final TenantProjectAuthorizer authorizer;

    InferenceController(InferenceGatewayUseCase useCase, TenantProjectAuthorizer authorizer) {
        this.useCase = useCase;
        this.authorizer = authorizer;
    }

    @PostMapping(path = "/stream", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    Flux<ServerSentEvent<InferenceStreamEventResponse>> stream(
            @Valid @RequestBody InferenceStreamRequest request,
            @RequestHeader(name = "traceparent", required = false) String traceparent
    ) {
        StartInferenceCommand command = new StartInferenceCommand(
                request.tenantId(),
                request.projectId(),
                request.provider(),
                request.model(),
                request.messages().stream()
                        .map(message -> new StartInferenceCommand.Message(toRole(message.role()), message.content()))
                        .toList(),
                nullToEmpty(request.parameters()),
                nullToEmptyStringMap(request.metadata()),
                request.clientRequestId(),
                nullToEmpty(request.streamOptions()),
                request.idempotencyKey(),
                traceparent);

        return authorizer.requireTenantProject(request.tenantId(), request.projectId(), "inference:write")
                .thenMany(useCase.stream(command))
                .map(this::toServerSentEvent);
    }

    @DeleteMapping(path = "/{requestId}/stream", produces = MediaType.APPLICATION_JSON_VALUE)
    Mono<CancelInferenceResponse> cancel(
            @PathVariable UUID requestId,
            @RequestHeader(name = "X-Requested-By", required = false) String requestedBy,
            @RequestHeader(name = "X-Cancel-Reason", required = false) String reason
    ) {
        return authorizer.requireScope("inference:write")
                .then(useCase.status(new GetInferenceStatusQuery(requestId)))
                .flatMap(status -> authorizer.requireTenantProject(status.tenantId(), status.projectId(), "inference:write"))
                .then(useCase.cancel(new CancelInferenceCommand(
                        requestId,
                        requestedBy == null || requestedBy.isBlank() ? "api" : requestedBy,
                        reason == null || reason.isBlank() ? "client_requested" : reason)))
                .map(result -> new CancelInferenceResponse(
                        result.requestId(),
                        result.conversationId(),
                        result.status().name(),
                        result.providerCancellationAttempted(),
                        result.providerCancellationSucceeded()));
    }

    @GetMapping(path = "/{requestId}", produces = MediaType.APPLICATION_JSON_VALUE)
    Mono<InferenceStatusResponse> status(@PathVariable UUID requestId) {
        return authorizer.requireScope("inference:read")
                .then(useCase.status(new GetInferenceStatusQuery(requestId)))
                .flatMap(result -> authorizer.requireTenantProject(result.tenantId(), result.projectId(), "inference:read")
                        .thenReturn(result))
                .map(InferenceStatusResponse::from);
    }

    private ServerSentEvent<InferenceStreamEventResponse> toServerSentEvent(StreamEvent event) {
        return ServerSentEvent.<InferenceStreamEventResponse>builder()
                .id(event.id())
                .event(event.type().wireName())
                .data(InferenceStreamEventResponse.from(event))
                .build();
    }

    private MessageRole toRole(String role) {
        return MessageRole.valueOf(role.toUpperCase(Locale.ROOT));
    }

    private Map<String, Object> nullToEmpty(Map<String, Object> value) {
        return value == null ? Map.of() : value;
    }

    private Map<String, String> nullToEmptyStringMap(Map<String, String> value) {
        return value == null ? Map.of() : value;
    }

    public record InferenceStreamRequest(
            @NotBlank String tenantId,
            @NotBlank String projectId,
            @NotBlank String provider,
            @NotBlank String model,
            @NotEmpty List<@Valid InferenceMessageRequest> messages,
            Map<String, Object> parameters,
            Map<String, String> metadata,
            String clientRequestId,
            Map<String, Object> streamOptions,
            @NotBlank String idempotencyKey
    ) {
    }

    public record InferenceMessageRequest(@NotBlank String role, @NotBlank String content) {
    }

    public record InferenceStreamEventResponse(
            String id,
            String type,
            UUID requestId,
            UUID conversationId,
            String traceId,
            long sequence,
            Instant occurredAt,
            Map<String, Object> data
    ) {
        static InferenceStreamEventResponse from(StreamEvent event) {
            return new InferenceStreamEventResponse(
                    event.id(),
                    event.type().wireName(),
                    event.requestId(),
                    event.conversationId(),
                    event.traceId(),
                    event.sequence(),
                    event.occurredAt(),
                    event.data());
        }
    }

    public record CancelInferenceResponse(
            UUID requestId,
            UUID conversationId,
            String status,
            boolean providerCancellationAttempted,
            boolean providerCancellationSucceeded
    ) {
        static CancelInferenceResponse from(com.llmobservability.platform.inferencegateway.application.port.in.CancelInferenceResult result) {
            return new CancelInferenceResponse(
                    result.requestId(),
                    result.conversationId(),
                    result.status().name(),
                    result.providerCancellationAttempted(),
                    result.providerCancellationSucceeded());
        }
    }
}
