package com.llmobservability.platform.inferencegateway.adapter.in.web;

import com.llmobservability.platform.inferencegateway.application.port.in.ContinueConversationCommand;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/v1/conversations")
public class ConversationController {
    private final InferenceGatewayUseCase useCase;

    ConversationController(InferenceGatewayUseCase useCase) {
        this.useCase = useCase;
    }

    @PostMapping(path = "/{conversationId}/messages/stream", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    Flux<ServerSentEvent<InferenceController.InferenceStreamEventResponse>> continueStream(
            @PathVariable UUID conversationId,
            @Valid @RequestBody ContinueConversationStreamRequest request,
            @RequestHeader(name = "traceparent", required = false) String traceparent
    ) {
        ContinueConversationCommand command = new ContinueConversationCommand(
                conversationId,
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

        return useCase.continueConversation(command)
                .map(this::toServerSentEvent);
    }

    private ServerSentEvent<InferenceController.InferenceStreamEventResponse> toServerSentEvent(StreamEvent event) {
        return ServerSentEvent.<InferenceController.InferenceStreamEventResponse>builder()
                .id(event.id())
                .event(event.type().wireName())
                .data(InferenceController.InferenceStreamEventResponse.from(event))
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

    public record ContinueConversationStreamRequest(
            @NotBlank String tenantId,
            @NotBlank String projectId,
            @NotBlank String provider,
            @NotBlank String model,
            @NotEmpty List<@Valid ContinueConversationMessageRequest> messages,
            Map<String, Object> parameters,
            Map<String, String> metadata,
            String clientRequestId,
            Map<String, Object> streamOptions,
            @NotBlank String idempotencyKey
    ) {
    }

    public record ContinueConversationMessageRequest(@NotBlank String role, @NotBlank String content) {
    }
}
