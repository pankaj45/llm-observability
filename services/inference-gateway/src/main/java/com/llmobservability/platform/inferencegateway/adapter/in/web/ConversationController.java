package com.llmobservability.platform.inferencegateway.adapter.in.web;

import com.llmobservability.platform.inferencegateway.application.port.in.CancelConversationStreamCommand;
import com.llmobservability.platform.inferencegateway.application.port.in.ConversationMessageResult;
import com.llmobservability.platform.inferencegateway.application.port.in.ConversationMetadataResult;
import com.llmobservability.platform.inferencegateway.application.port.in.ConversationTimelineEventResult;
import com.llmobservability.platform.inferencegateway.application.port.in.ContinueConversationCommand;
import com.llmobservability.platform.inferencegateway.application.port.in.GetConversationQuery;
import com.llmobservability.platform.inferencegateway.application.port.in.InferenceGatewayUseCase;
import com.llmobservability.platform.inferencegateway.application.port.in.ListConversationEventsQuery;
import com.llmobservability.platform.inferencegateway.application.port.in.ListConversationMessagesQuery;
import com.llmobservability.platform.inferencegateway.application.port.in.ListConversationsQuery;
import com.llmobservability.platform.inferencegateway.application.port.in.PagedResult;
import com.llmobservability.platform.inferencegateway.application.port.in.StartInferenceCommand;
import com.llmobservability.platform.inferencegateway.application.port.in.StreamConversationEventsQuery;
import com.llmobservability.platform.inferencegateway.domain.model.ConversationStatus;
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
import org.springframework.web.bind.annotation.RequestParam;
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
@RequestMapping("/v1/conversations")
public class ConversationController {
    private final InferenceGatewayUseCase useCase;

    ConversationController(InferenceGatewayUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    Mono<PagedResult<ConversationMetadataResponse>> listConversations(
            @RequestParam @NotBlank String tenantId,
            @RequestParam @NotBlank String projectId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") int limit
    ) {
        ConversationStatus conversationStatus = status == null || status.isBlank()
                ? null
                : ConversationStatus.valueOf(status.toUpperCase(Locale.ROOT));
        return useCase.listConversations(new ListConversationsQuery(tenantId, projectId, conversationStatus, cursor, limit))
                .map(page -> new PagedResult<>(page.items().stream().map(ConversationMetadataResponse::from).toList(), page.nextCursor()));
    }

    @GetMapping(path = "/{conversationId}", produces = MediaType.APPLICATION_JSON_VALUE)
    Mono<ConversationMetadataResponse> getConversation(
            @PathVariable UUID conversationId,
            @RequestParam @NotBlank String tenantId,
            @RequestParam @NotBlank String projectId
    ) {
        return useCase.getConversation(new GetConversationQuery(conversationId, tenantId, projectId))
                .map(ConversationMetadataResponse::from);
    }

    @GetMapping(path = "/{conversationId}/messages", produces = MediaType.APPLICATION_JSON_VALUE)
    Mono<PagedResult<ConversationMessageResponse>> listMessages(
            @PathVariable UUID conversationId,
            @RequestParam @NotBlank String tenantId,
            @RequestParam @NotBlank String projectId,
            @RequestParam(required = false) String after,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return useCase.listConversationMessages(new ListConversationMessagesQuery(conversationId, tenantId, projectId, after, limit))
                .map(page -> new PagedResult<>(page.items().stream().map(ConversationMessageResponse::from).toList(), page.nextCursor()));
    }

    @GetMapping(path = "/{conversationId}/events", params = "mode=stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    Flux<ServerSentEvent<InferenceController.InferenceStreamEventResponse>> streamEvents(
            @PathVariable UUID conversationId,
            @RequestParam @NotBlank String tenantId,
            @RequestParam @NotBlank String projectId,
            @RequestParam(required = false) String after
    ) {
        return useCase.streamConversationEvents(new StreamConversationEventsQuery(conversationId, tenantId, projectId, after))
                .map(this::toServerSentEvent);
    }

    @GetMapping(path = "/{conversationId}/events", produces = MediaType.APPLICATION_JSON_VALUE)
    Mono<PagedResult<ConversationTimelineEventResponse>> listEvents(
            @PathVariable UUID conversationId,
            @RequestParam @NotBlank String tenantId,
            @RequestParam @NotBlank String projectId,
            @RequestParam(required = false) String after,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return useCase.listConversationEvents(new ListConversationEventsQuery(conversationId, tenantId, projectId, after, limit))
                .map(page -> new PagedResult<>(page.items().stream().map(ConversationTimelineEventResponse::from).toList(), page.nextCursor()));
    }

    @DeleteMapping(path = "/{conversationId}/stream", produces = MediaType.APPLICATION_JSON_VALUE)
    Mono<InferenceController.CancelInferenceResponse> cancelConversationStream(
            @PathVariable UUID conversationId,
            @RequestParam @NotBlank String tenantId,
            @RequestParam @NotBlank String projectId,
            @RequestHeader(name = "X-Requested-By", defaultValue = "unknown") String requestedBy,
            @RequestHeader(name = "X-Cancel-Reason", defaultValue = "conversation stream cancelled") String reason
    ) {
        return useCase.cancelConversationStream(new CancelConversationStreamCommand(conversationId, tenantId, projectId, requestedBy, reason))
                .map(InferenceController.CancelInferenceResponse::from);
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

    public record ConversationMetadataResponse(
            UUID conversationId,
            String tenantId,
            String projectId,
            String status,
            String title,
            String titleSource,
            Instant createdAt,
            Instant updatedAt,
            Instant cancelledAt,
            Instant lastMessageAt,
            long messageCount,
            UUID activeRequestId,
            String latestRequestStatus
    ) {
        static ConversationMetadataResponse from(ConversationMetadataResult result) {
            return new ConversationMetadataResponse(
                    result.conversationId(),
                    result.tenantId(),
                    result.projectId(),
                    result.status().name(),
                    result.title(),
                    result.titleSource(),
                    result.createdAt(),
                    result.updatedAt(),
                    result.cancelledAt(),
                    result.lastMessageAt(),
                    result.messageCount(),
                    result.activeRequestId(),
                    result.latestRequestStatus() == null ? null : result.latestRequestStatus().name());
        }
    }

    public record ConversationMessageResponse(
            UUID messageId,
            UUID conversationId,
            String role,
            int sequence,
            String content,
            String contentHash,
            int estimatedTokens,
            String redactionState,
            Map<String, String> metadata,
            Instant createdAt,
            String cursor
    ) {
        static ConversationMessageResponse from(ConversationMessageResult result) {
            return new ConversationMessageResponse(
                    result.messageId(),
                    result.conversationId(),
                    result.role().name(),
                    result.sequence(),
                    result.content(),
                    result.contentHash(),
                    result.estimatedTokens(),
                    result.redactionState().name(),
                    result.metadata(),
                    result.createdAt(),
                    result.cursor());
        }
    }

    public record ConversationTimelineEventResponse(
            String id,
            String type,
            UUID conversationId,
            UUID requestId,
            UUID messageId,
            long sequence,
            Instant occurredAt,
            Map<String, Object> data,
            String cursor
    ) {
        static ConversationTimelineEventResponse from(ConversationTimelineEventResult result) {
            return new ConversationTimelineEventResponse(
                    result.id(),
                    result.type(),
                    result.conversationId(),
                    result.requestId(),
                    result.messageId(),
                    result.sequence(),
                    result.occurredAt(),
                    result.data(),
                    result.cursor());
        }
    }
}
