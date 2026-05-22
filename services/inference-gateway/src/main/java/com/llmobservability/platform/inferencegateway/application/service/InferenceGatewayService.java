package com.llmobservability.platform.inferencegateway.application.service;

import com.llmobservability.platform.inferencegateway.application.port.in.CancelInferenceCommand;
import com.llmobservability.platform.inferencegateway.application.port.in.CancelInferenceResult;
import com.llmobservability.platform.inferencegateway.application.port.in.ContinueConversationCommand;
import com.llmobservability.platform.inferencegateway.application.port.in.ErrorSummary;
import com.llmobservability.platform.inferencegateway.application.port.in.GetInferenceStatusQuery;
import com.llmobservability.platform.inferencegateway.application.port.in.InferenceGatewayUseCase;
import com.llmobservability.platform.inferencegateway.application.port.in.InferenceStatusResult;
import com.llmobservability.platform.inferencegateway.application.port.in.StartInferenceCommand;
import com.llmobservability.platform.inferencegateway.application.port.in.UsageSummary;
import com.llmobservability.platform.inferencegateway.application.port.out.ActiveStreamStateStore;
import com.llmobservability.platform.inferencegateway.application.port.out.ConversationMessageRepository;
import com.llmobservability.platform.inferencegateway.application.port.out.ConversationRepository;
import com.llmobservability.platform.inferencegateway.application.port.out.InferenceCancellationRepository;
import com.llmobservability.platform.inferencegateway.application.port.out.InferenceErrorRepository;
import com.llmobservability.platform.inferencegateway.application.port.out.InferenceRequestRepository;
import com.llmobservability.platform.inferencegateway.application.port.out.InferenceUsageRepository;
import com.llmobservability.platform.inferencegateway.application.port.out.LifecycleEventPublisher;
import com.llmobservability.platform.inferencegateway.application.port.out.ModelCatalogRepository;
import com.llmobservability.platform.inferencegateway.application.port.out.ProviderClient;
import com.llmobservability.platform.inferencegateway.application.port.out.ProviderClientRegistry;
import com.llmobservability.platform.inferencegateway.config.InferenceGatewayProperties;
import com.llmobservability.platform.inferencegateway.domain.model.Conversation;
import com.llmobservability.platform.inferencegateway.domain.model.ConversationMessage;
import com.llmobservability.platform.inferencegateway.domain.model.ConversationStatus;
import com.llmobservability.platform.inferencegateway.domain.model.ErrorCode;
import com.llmobservability.platform.inferencegateway.domain.model.FailureStage;
import com.llmobservability.platform.inferencegateway.domain.model.InferenceCancellation;
import com.llmobservability.platform.inferencegateway.domain.model.InferenceError;
import com.llmobservability.platform.inferencegateway.domain.model.InferenceRequest;
import com.llmobservability.platform.inferencegateway.domain.model.InferenceStatus;
import com.llmobservability.platform.inferencegateway.domain.model.InferenceUsage;
import com.llmobservability.platform.inferencegateway.domain.model.MessageRole;
import com.llmobservability.platform.inferencegateway.domain.model.ModelCatalogEntry;
import com.llmobservability.platform.inferencegateway.domain.model.RedactionState;
import com.llmobservability.platform.inferencegateway.domain.model.StreamEvent;
import com.llmobservability.platform.inferencegateway.domain.model.StreamEventType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class InferenceGatewayService implements InferenceGatewayUseCase {
    private final ConversationRepository conversationRepository;
    private final ConversationMessageRepository conversationMessageRepository;
    private final InferenceRequestRepository inferenceRequestRepository;
    private final InferenceUsageRepository inferenceUsageRepository;
    private final InferenceErrorRepository inferenceErrorRepository;
    private final InferenceCancellationRepository inferenceCancellationRepository;
    private final ModelCatalogRepository modelCatalogRepository;
    private final ActiveStreamStateStore activeStreamStateStore;
    private final ProviderClientRegistry providerClientRegistry;
    private final LifecycleEventPublisher lifecycleEventPublisher;
    private final ConversationTitlePolicy titlePolicy;
    private final InferenceGatewayProperties properties;
    private final MeterRegistry meterRegistry;

    public InferenceGatewayService(
            ConversationRepository conversationRepository,
            ConversationMessageRepository conversationMessageRepository,
            InferenceRequestRepository inferenceRequestRepository,
            InferenceUsageRepository inferenceUsageRepository,
            InferenceErrorRepository inferenceErrorRepository,
            InferenceCancellationRepository inferenceCancellationRepository,
            ModelCatalogRepository modelCatalogRepository,
            ActiveStreamStateStore activeStreamStateStore,
            ProviderClientRegistry providerClientRegistry,
            LifecycleEventPublisher lifecycleEventPublisher,
            ConversationTitlePolicy titlePolicy,
            InferenceGatewayProperties properties,
            MeterRegistry meterRegistry
    ) {
        this.conversationRepository = conversationRepository;
        this.conversationMessageRepository = conversationMessageRepository;
        this.inferenceRequestRepository = inferenceRequestRepository;
        this.inferenceUsageRepository = inferenceUsageRepository;
        this.inferenceErrorRepository = inferenceErrorRepository;
        this.inferenceCancellationRepository = inferenceCancellationRepository;
        this.modelCatalogRepository = modelCatalogRepository;
        this.activeStreamStateStore = activeStreamStateStore;
        this.providerClientRegistry = providerClientRegistry;
        this.lifecycleEventPublisher = lifecycleEventPublisher;
        this.titlePolicy = titlePolicy;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public Flux<StreamEvent> stream(StartInferenceCommand command) {
        Timer.Sample sample = Timer.start(meterRegistry);
        return validate(command)
                .thenMany(inferenceRequestRepository.findByIdempotencyKey(command.tenantId(), command.projectId(), command.idempotencyKey())
                        .flatMapMany(existing -> Flux.<StreamEvent>error(new ApplicationException(
                                ErrorCode.VALIDATION_INVALID_REQUEST,
                                FailureStage.VALIDATION,
                                "idempotencyKey has already been used for request " + existing.id())))
                        .switchIfEmpty(Mono.defer(() -> prepareStream(command)).flatMapMany(prepared -> streamPrepared(prepared, execution(command)))))
                .doFinally(signalType -> sample.stop(Timer.builder("inference_request_duration_seconds")
                        .description("Inference stream lifecycle duration")
                        .tag("provider", command.provider())
                        .tag("model", command.model())
                        .register(meterRegistry)));
    }

    @Override
    public Flux<StreamEvent> continueConversation(ContinueConversationCommand command) {
        Timer.Sample sample = Timer.start(meterRegistry);
        return validate(command)
                .thenMany(inferenceRequestRepository.findByIdempotencyKey(command.tenantId(), command.projectId(), command.idempotencyKey())
                        .flatMapMany(existing -> Flux.<StreamEvent>error(new ApplicationException(
                                ErrorCode.VALIDATION_INVALID_REQUEST,
                                FailureStage.VALIDATION,
                                "idempotencyKey has already been used for request " + existing.id())))
                        .switchIfEmpty(Mono.defer(() -> prepareContinuation(command)).flatMapMany(tuple -> streamPrepared(tuple.prepared(), tuple.execution()))))
                .doFinally(signalType -> sample.stop(Timer.builder("inference_request_duration_seconds")
                        .description("Inference stream lifecycle duration")
                        .tag("provider", command.provider())
                        .tag("model", command.model())
                        .register(meterRegistry)));
    }

    @Override
    public Mono<CancelInferenceResult> cancel(CancelInferenceCommand command) {
        Instant now = Instant.now();
        return inferenceRequestRepository.findById(command.requestId())
                .switchIfEmpty(Mono.error(new ApplicationException(
                        ErrorCode.VALIDATION_INVALID_REQUEST,
                        FailureStage.VALIDATION,
                        "Inference request was not found")))
                .flatMap(request -> {
                    if (!request.active()) {
                        return Mono.error(new ApplicationException(
                                ErrorCode.STREAM_CANCELLED,
                                FailureStage.STREAMING,
                                "Inference request is not active"));
                    }

                    return activeStreamStateStore.requestCancellation(request.id(), properties.activeStreamTtl())
                            .then(providerClientRegistry.get(request.providerKey()))
                            .flatMap(providerClient -> providerClient.cancel(request.id()))
                            .flatMap(providerResult -> {
                                InferenceCancellation cancellation = new InferenceCancellation(
                                        UUID.randomUUID(),
                                        request.id(),
                                        command.requestedBy(),
                                        command.reason(),
                                        providerResult.attempted(),
                                        providerResult.succeeded(),
                                        now,
                                        Instant.now());
                                return inferenceCancellationRepository.save(cancellation)
                                        .then(inferenceRequestRepository.markCancelled(request.id(), now))
                                        .then(publish("inference.cancelled", request, Map.of(
                                                "requestId", request.id().toString(),
                                                "conversationId", request.conversationId().toString(),
                                                "reason", command.reason(),
                                                "providerCancellationAttempted", providerResult.attempted(),
                                                "providerCancellationSucceeded", providerResult.succeeded())))
                                        .thenReturn(new CancelInferenceResult(
                                                request.id(),
                                                request.conversationId(),
                                                InferenceStatus.CANCELLED,
                                                providerResult.attempted(),
                                                providerResult.succeeded()));
                            });
                })
                .doOnSuccess(result -> Counter.builder("inference_stream_cancellations_total")
                        .description("Accepted inference stream cancellations")
                        .register(meterRegistry)
                        .increment());
    }

    @Override
    public Mono<InferenceStatusResult> status(GetInferenceStatusQuery query) {
        return inferenceRequestRepository.findById(query.requestId())
                .switchIfEmpty(Mono.error(new ApplicationException(
                        ErrorCode.VALIDATION_INVALID_REQUEST,
                        FailureStage.VALIDATION,
                        "Inference request was not found")))
                .flatMap(request -> Mono.zip(
                                inferenceUsageRepository.findByRequestId(request.id()).defaultIfEmpty(emptyUsage(request.id())),
                                inferenceErrorRepository.findLatest(request.id()).defaultIfEmpty(emptyError(request.id()))
                        )
                        .map(tuple -> toStatusResult(request, tuple.getT1(), tuple.getT2())));
    }

    private Mono<Void> validate(StartInferenceCommand command) {
        if (!"gemini".equals(command.provider())) {
            return Mono.error(new ApplicationException(
                    ErrorCode.PROVIDER_UNSUPPORTED,
                    FailureStage.VALIDATION,
                    "Only provider 'gemini' is supported in phase 2"));
        }
        if (command.messages() == null || command.messages().isEmpty()) {
            return Mono.error(new ApplicationException(
                    ErrorCode.VALIDATION_INVALID_REQUEST,
                    FailureStage.VALIDATION,
                    "messages must not be empty"));
        }
        if (command.idempotencyKey() == null || command.idempotencyKey().isBlank()) {
            return Mono.error(new ApplicationException(
                    ErrorCode.VALIDATION_INVALID_REQUEST,
                    FailureStage.VALIDATION,
                    "idempotencyKey must not be blank"));
        }
        return Mono.empty();
    }

    private Mono<Void> validate(ContinueConversationCommand command) {
        if (!"gemini".equals(command.provider())) {
            return Mono.error(new ApplicationException(
                    ErrorCode.PROVIDER_UNSUPPORTED,
                    FailureStage.VALIDATION,
                    "Only provider 'gemini' is supported in phase 2"));
        }
        if (command.conversationId() == null) {
            return Mono.error(new ApplicationException(
                    ErrorCode.CONVERSATION_NOT_FOUND,
                    FailureStage.VALIDATION,
                    "conversationId must be provided"));
        }
        if (command.messages() == null || command.messages().isEmpty()) {
            return Mono.error(new ApplicationException(
                    ErrorCode.VALIDATION_INVALID_REQUEST,
                    FailureStage.VALIDATION,
                    "messages must not be empty"));
        }
        if (command.idempotencyKey() == null || command.idempotencyKey().isBlank()) {
            return Mono.error(new ApplicationException(
                    ErrorCode.VALIDATION_INVALID_REQUEST,
                    FailureStage.VALIDATION,
                    "idempotencyKey must not be blank"));
        }
        return Mono.empty();
    }

    private Mono<PreparedStream> prepareStream(StartInferenceCommand command) {
        UUID requestId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        Instant now = Instant.now();
        ConversationTitlePolicy.Title title = titlePolicy.title(command);
        String redisStreamKey = "inference:stream:" + requestId;

        Conversation conversation = new Conversation(
                conversationId,
                command.tenantId(),
                command.projectId(),
                ConversationStatus.ACTIVE,
                title.value(),
                title.source(),
                now,
                now,
                null);

        return modelCatalogRepository.findEnabledModel(command.provider(), command.model())
                .switchIfEmpty(Mono.error(new ApplicationException(
                        ErrorCode.PROVIDER_UNSUPPORTED,
                        FailureStage.VALIDATION,
                        "Model is not enabled for provider")))
                .flatMap(model -> conversationRepository.save(conversation)
                        .then(conversationMessageRepository.saveAll(toConversationMessages(command, conversationId, now)))
                        .then(Mono.just(createRequest(command, requestId, conversationId, model, redisStreamKey, now)))
                        .flatMap(inferenceRequestRepository::save)
                        .flatMap(request -> activeStreamStateStore.register(request.id(), request.conversationId(), properties.activeStreamTtl())
                                .then(publish("inference.requested", request, command.traceId(), requestedPayload(request)))
                                .then(providerClientRegistry.get(command.provider()))
                                .map(providerClient -> PreparedStream.create(request, model, providerClient))));
    }

    private Mono<PreparedContinuation> prepareContinuation(ContinueConversationCommand command) {
        UUID requestId = UUID.randomUUID();
        Instant now = Instant.now();
        String redisStreamKey = "inference:stream:" + requestId;

        return conversationRepository.findById(command.conversationId())
                .switchIfEmpty(Mono.error(new ApplicationException(
                        ErrorCode.CONVERSATION_NOT_FOUND,
                        FailureStage.VALIDATION,
                        "Conversation was not found")))
                .flatMap(conversation -> {
                    if (!conversation.tenantId().equals(command.tenantId()) || !conversation.projectId().equals(command.projectId())) {
                        return Mono.error(new ApplicationException(
                                ErrorCode.CONVERSATION_NOT_FOUND,
                                FailureStage.VALIDATION,
                                "Conversation was not found"));
                    }
                    if (conversation.status() != ConversationStatus.ACTIVE) {
                        return Mono.error(new ApplicationException(
                                ErrorCode.VALIDATION_INVALID_REQUEST,
                                FailureStage.VALIDATION,
                                "Conversation is not active"));
                    }
                    return conversationMessageRepository.findByConversationId(conversation.id())
                            .collectList()
                            .flatMap(existingMessages -> prepareContinuation(command, requestId, now, redisStreamKey, existingMessages));
                });
    }

    private Mono<PreparedContinuation> prepareContinuation(
            ContinueConversationCommand command,
            UUID requestId,
            Instant now,
            String redisStreamKey,
            List<ConversationMessage> existingMessages
    ) {
        List<ConversationMessage> orderedExistingMessages = existingMessages.stream()
                .sorted((left, right) -> Integer.compare(left.sequence(), right.sequence()))
                .toList();
        int nextSequence = orderedExistingMessages.stream()
                .mapToInt(ConversationMessage::sequence)
                .max()
                .orElse(-1) + 1;
        List<ConversationMessage> newMessages = toConversationMessages(command, nextSequence, now);
        List<StartInferenceCommand.Message> providerMessages = new ArrayList<>();
        providerMessages.addAll(orderedExistingMessages.stream()
                .map(message -> new StartInferenceCommand.Message(message.role(), message.content()))
                .toList());
        providerMessages.addAll(command.messages());

        return modelCatalogRepository.findEnabledModel(command.provider(), command.model())
                .switchIfEmpty(Mono.error(new ApplicationException(
                        ErrorCode.PROVIDER_UNSUPPORTED,
                        FailureStage.VALIDATION,
                        "Model is not enabled for provider")))
                .flatMap(model -> conversationMessageRepository.saveAll(newMessages)
                        .then(Mono.just(createRequest(command, requestId, model, redisStreamKey, providerMessages, now)))
                        .flatMap(inferenceRequestRepository::save)
                        .flatMap(request -> activeStreamStateStore.register(request.id(), request.conversationId(), properties.activeStreamTtl())
                                .then(publish("inference.requested", request, command.traceId(), requestedPayload(request)))
                                .then(providerClientRegistry.get(command.provider()))
                                .map(providerClient -> new PreparedContinuation(
                                        PreparedStream.create(request, model, providerClient),
                                        execution(command, providerMessages)))));
    }

    private Flux<StreamEvent> streamPrepared(PreparedStream prepared, StreamExecution execution) {
        InferenceRequest request = prepared.request();
        ProviderClient providerClient = prepared.providerClient();
        AtomicLong sequence = new AtomicLong(0);
        AtomicBoolean firstTokenSeen = new AtomicBoolean(false);
        AtomicInteger inputTokens = new AtomicInteger(0);
        AtomicInteger outputTokens = new AtomicInteger(0);
        StringBuilder assistantContent = new StringBuilder();

        Flux<StreamEvent> lifecycle = Flux.concat(
                Mono.just(event(StreamEventType.REQUEST_ACCEPTED, request, sequence.incrementAndGet(), execution.traceId(), Map.of(
                        "status", InferenceStatus.ACCEPTED.name(),
                        "conversation", execution.conversationState()))),
                providerClient.stream(toProviderRequest(request, execution))
                        .flatMap(chunk -> activeStreamStateStore.cancellationRequested(request.id())
                                .flatMap(cancelled -> {
                                    if (Boolean.TRUE.equals(cancelled)) {
                                        return Mono.error(new ApplicationException(
                                                ErrorCode.STREAM_CANCELLED,
                                                FailureStage.STREAMING,
                                                "Inference stream was cancelled"));
                                    }
                                    return Mono.just(chunk);
                                }))
                        .flatMap(chunk -> {
                            if (chunk.inputTokens() != null) {
                                inputTokens.set(chunk.inputTokens());
                            }
                            if (chunk.outputTokens() != null) {
                                outputTokens.set(chunk.outputTokens());
                            }
                            if (chunk.text() != null && !chunk.text().isBlank()) {
                                assistantContent.append(chunk.text());
                            }

                            Mono<Void> markStreaming = Mono.empty();
                            if (firstTokenSeen.compareAndSet(false, true)) {
                                markStreaming = inferenceRequestRepository.markStreaming(request.id(), Instant.now());
                                Timer.builder("inference_first_token_latency_seconds")
                                        .description("Time from accepted request to first provider token")
                                        .tag("provider", request.providerKey())
                                        .tag("model", request.modelKey())
                                        .register(meterRegistry)
                                        .record(java.time.Duration.between(request.createdAt(), Instant.now()));
                            }

                            StreamEventType eventType = chunk.text() == null || chunk.text().isBlank()
                                    ? StreamEventType.USAGE_DELTA
                                    : StreamEventType.TOKEN_DELTA;
                            Map<String, Object> data = new LinkedHashMap<>();
                            data.put("status", InferenceStatus.STREAMING.name());
                            data.put("delta", chunk.text());
                            data.put("inputTokens", inputTokens.get());
                            data.put("outputTokens", outputTokens.get());

                            return markStreaming.thenReturn(event(eventType, request, sequence.incrementAndGet(), execution.traceId(), data));
                        }),
                Mono.defer(() -> completeRequest(request, assistantContent.toString(), inputTokens.get(), outputTokens.get(), sequence, execution.traceId()))
        );

        return lifecycle
                .publish(shared -> Flux.merge(shared, heartbeat(request, execution.traceId(), sequence).takeUntilOther(shared.ignoreElements())))
                .onErrorResume(error -> {
                    if (error instanceof ApplicationException applicationException
                            && applicationException.errorCode() == ErrorCode.STREAM_CANCELLED) {
                        return cancelStream(request, sequence, execution.traceId());
                    }
                    return failRequest(request, error, sequence, execution.traceId());
                })
                .doFinally(signalType -> activeStreamStateStore.clear(request.id()).subscribe());
    }

    private Mono<StreamEvent> completeRequest(InferenceRequest request, String assistantContent, int inputTokens, int outputTokens, AtomicLong sequence, String traceId) {
        Instant now = Instant.now();
        String outputHash = ContentHasher.sha256(assistantContent);
        ConversationMessage assistantMessage = new ConversationMessage(
                UUID.randomUUID(),
                request.conversationId(),
                MessageRole.ASSISTANT,
                request.inputMessageCount(),
                assistantContent,
                outputHash,
                estimateTokens(assistantContent),
                RedactionState.NONE,
                Map.of("source", "gemini"),
                now);
        InferenceUsage usage = new InferenceUsage(
                UUID.randomUUID(),
                request.id(),
                inputTokens,
                outputTokens,
                inputTokens + outputTokens,
                "tokens",
                BigDecimal.ZERO,
                "USD",
                now);

        return conversationMessageRepository.save(assistantMessage)
                .then(inferenceUsageRepository.save(usage))
                .then(inferenceRequestRepository.markCompleted(request.id(), outputHash, now))
                .then(publish("inference.completed", request, traceId, Map.of(
                        "requestId", request.id().toString(),
                        "conversationId", request.conversationId().toString(),
                        "provider", request.providerKey(),
                        "model", request.modelKey(),
                        "status", InferenceStatus.COMPLETED.name(),
                        "inputTokens", inputTokens,
                        "outputTokens", outputTokens,
                        "totalTokens", inputTokens + outputTokens,
                        "durationMs", java.time.Duration.between(request.createdAt(), now).toMillis())))
                .thenReturn(event(StreamEventType.REQUEST_COMPLETED, request, sequence.incrementAndGet(), traceId, Map.of(
                        "status", InferenceStatus.COMPLETED.name(),
                        "inputTokens", inputTokens,
                        "outputTokens", outputTokens,
                        "totalTokens", inputTokens + outputTokens)));
    }

    private Flux<StreamEvent> cancelStream(InferenceRequest request, AtomicLong sequence, String traceId) {
        Instant now = Instant.now();
        return inferenceRequestRepository.markCancelled(request.id(), now)
                .then(publish("inference.cancelled", request, traceId, Map.of(
                        "requestId", request.id().toString(),
                        "conversationId", request.conversationId().toString(),
                        "provider", request.providerKey(),
                        "model", request.modelKey(),
                        "status", InferenceStatus.CANCELLED.name())))
                .thenMany(Flux.just(event(StreamEventType.REQUEST_CANCELLED, request, sequence.incrementAndGet(), traceId, Map.of(
                        "status", InferenceStatus.CANCELLED.name()))));
    }

    private Flux<StreamEvent> failRequest(InferenceRequest request, Throwable throwable, AtomicLong sequence, String traceId) {
        ApplicationException exception = throwable instanceof ApplicationException appException
                ? appException
                : new ApplicationException(
                ErrorCode.INTERNAL_PROVIDER_ERROR,
                FailureStage.PROVIDER,
                null,
                "Provider stream failed",
                true,
                throwable);
        Instant now = Instant.now();
        InferenceError error = new InferenceError(
                UUID.randomUUID(),
                request.id(),
                exception.failureStage(),
                exception.errorCode().code(),
                exception.providerErrorCode(),
                exception.getMessage(),
                exception.retryable(),
                now);
        return inferenceErrorRepository.save(error)
                .then(inferenceRequestRepository.markFailed(request.id(), now))
                .then(publish("inference.failed", request, traceId, Map.of(
                        "requestId", request.id().toString(),
                        "conversationId", request.conversationId().toString(),
                        "provider", request.providerKey(),
                        "model", request.modelKey(),
                        "status", InferenceStatus.FAILED.name(),
                        "failureStage", exception.failureStage().name(),
                        "errorCode", exception.errorCode().code(),
                        "retryable", exception.retryable())))
                .thenMany(Flux.just(event(StreamEventType.REQUEST_FAILED, request, sequence.incrementAndGet(), traceId, Map.of(
                        "status", InferenceStatus.FAILED.name(),
                        "errorCode", exception.errorCode().code(),
                        "message", exception.getMessage()))));
    }

    private Flux<StreamEvent> heartbeat(InferenceRequest request, String traceId, AtomicLong sequence) {
        return Flux.interval(properties.streamHeartbeat())
                .map(ignored -> event(StreamEventType.HEARTBEAT, request, sequence.incrementAndGet(), traceId, Map.of(
                        "status", "active")));
    }

    private InferenceRequest createRequest(StartInferenceCommand command, UUID requestId, UUID conversationId, ModelCatalogEntry model, String redisStreamKey, Instant now) {
        return createRequest(
                requestId,
                command.tenantId(),
                command.projectId(),
                conversationId,
                model,
                command.idempotencyKey(),
                command.metadata(),
                redisStreamKey,
                command.messages(),
                now);
    }

    private InferenceRequest createRequest(
            ContinueConversationCommand command,
            UUID requestId,
            ModelCatalogEntry model,
            String redisStreamKey,
            List<StartInferenceCommand.Message> providerMessages,
            Instant now
    ) {
        return createRequest(
                requestId,
                command.tenantId(),
                command.projectId(),
                command.conversationId(),
                model,
                command.idempotencyKey(),
                command.metadata(),
                redisStreamKey,
                providerMessages,
                now);
    }

    private InferenceRequest createRequest(
            UUID requestId,
            String tenantId,
            String projectId,
            UUID conversationId,
            ModelCatalogEntry model,
            String idempotencyKey,
            Map<String, String> metadata,
            String redisStreamKey,
            List<StartInferenceCommand.Message> providerMessages,
            Instant now
    ) {
        List<String> content = providerMessages.stream().map(StartInferenceCommand.Message::content).toList();
        return new InferenceRequest(
                requestId,
                tenantId,
                projectId,
                conversationId,
                model.providerId(),
                model.modelId(),
                model.providerKey(),
                model.modelKey(),
                idempotencyKey,
                InferenceStatus.ACCEPTED,
                true,
                metadata,
                providerMessages.size(),
                ContentHasher.sha256Joined(content),
                null,
                redisStreamKey,
                now,
                now,
                null,
                null,
                null,
                null,
                now);
    }

    private List<ConversationMessage> toConversationMessages(StartInferenceCommand command, UUID conversationId, Instant now) {
        List<ConversationMessage> messages = new ArrayList<>();
        for (int i = 0; i < command.messages().size(); i++) {
            StartInferenceCommand.Message message = command.messages().get(i);
            messages.add(new ConversationMessage(
                    UUID.randomUUID(),
                    conversationId,
                    message.role(),
                    i,
                    message.content(),
                    ContentHasher.sha256(message.content()),
                    estimateTokens(message.content()),
                    RedactionState.NONE,
                    Map.of("source", "request"),
                    now));
        }
        return messages;
    }

    private List<ConversationMessage> toConversationMessages(ContinueConversationCommand command, int startingSequence, Instant now) {
        List<ConversationMessage> messages = new ArrayList<>();
        for (int i = 0; i < command.messages().size(); i++) {
            StartInferenceCommand.Message message = command.messages().get(i);
            messages.add(new ConversationMessage(
                    UUID.randomUUID(),
                    command.conversationId(),
                    message.role(),
                    startingSequence + i,
                    message.content(),
                    ContentHasher.sha256(message.content()),
                    estimateTokens(message.content()),
                    RedactionState.NONE,
                    Map.of("source", "request"),
                    now));
        }
        return messages;
    }

    private ProviderClient.ProviderRequest toProviderRequest(InferenceRequest request, StreamExecution execution) {
        List<ProviderClient.ProviderMessage> messages = execution.providerMessages().stream()
                .map(message -> new ProviderClient.ProviderMessage(message.role().name().toLowerCase(), message.content()))
                .toList();
        return new ProviderClient.ProviderRequest(request.id(), request.modelKey(), messages, execution.parameters());
    }

    private StreamExecution execution(StartInferenceCommand command) {
        return new StreamExecution(command.parameters(), command.traceId(), command.messages(), "created");
    }

    private StreamExecution execution(ContinueConversationCommand command, List<StartInferenceCommand.Message> providerMessages) {
        return new StreamExecution(command.parameters(), command.traceId(), providerMessages, "continued");
    }

    private Map<String, Object> requestedPayload(InferenceRequest request) {
        return Map.of(
                "requestId", request.id().toString(),
                "conversationId", request.conversationId().toString(),
                "provider", request.providerKey(),
                "model", request.modelKey(),
                "status", request.status().name(),
                "streaming", request.streaming(),
                "inputMessageCount", request.inputMessageCount(),
                "inputContentHash", request.inputContentHash());
    }

    private Mono<Void> publish(String eventName, InferenceRequest request, Map<String, Object> payload) {
        return publish(eventName, request, null, payload);
    }

    private Mono<Void> publish(String eventName, InferenceRequest request, String traceparent, Map<String, Object> payload) {
        return lifecycleEventPublisher.publish(
                eventName,
                request.tenantId(),
                request.projectId(),
                request.id().toString(),
                traceparent,
                request.idempotencyKey() + ":" + eventName,
                payload);
    }

    private StreamEvent event(StreamEventType type, InferenceRequest request, long sequence, String traceId, Map<String, Object> data) {
        return new StreamEvent(
                request.id() + ":" + sequence,
                type,
                request.id(),
                request.conversationId(),
                traceId,
                sequence,
                Instant.now(),
                data);
    }

    private InferenceStatusResult toStatusResult(InferenceRequest request, InferenceUsage usage, InferenceError error) {
        UsageSummary usageSummary = usage.inferenceRequestId() == null
                ? null
                : new UsageSummary(usage.inputTokens(), usage.outputTokens(), usage.totalTokens(), usage.estimatedCostAmount(), usage.estimatedCostCurrency());
        ErrorSummary errorSummary = error.inferenceRequestId() == null
                ? null
                : new ErrorSummary(error.failureStage(), error.errorCode(), error.providerErrorCode(), error.message(), error.retryable());
        return new InferenceStatusResult(
                request.id(),
                request.conversationId(),
                request.tenantId(),
                request.projectId(),
                request.providerKey(),
                request.modelKey(),
                request.status(),
                request.createdAt(),
                request.startedAt(),
                request.firstTokenAt(),
                request.completedAt(),
                request.cancelledAt(),
                request.failedAt(),
                usageSummary,
                errorSummary,
                request.requestMetadata());
    }

    private InferenceUsage emptyUsage(UUID requestId) {
        return new InferenceUsage(null, null, 0, 0, 0, null, null, null, null);
    }

    private InferenceError emptyError(UUID requestId) {
        return new InferenceError(null, null, null, null, null, null, false, null);
    }

    private int estimateTokens(String content) {
        if (content == null || content.isBlank()) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(content.length() / 4.0));
    }

    private record PreparedStream(InferenceRequest request, ModelCatalogEntry model, ProviderClient providerClient) {
        private static PreparedStream create(InferenceRequest request, ModelCatalogEntry model, ProviderClient providerClient) {
            return new PreparedStream(request, model, providerClient);
        }
    }

    private record PreparedContinuation(PreparedStream prepared, StreamExecution execution) {
    }

    private record StreamExecution(
            Map<String, Object> parameters,
            String traceId,
            List<StartInferenceCommand.Message> providerMessages,
            String conversationState
    ) {
    }
}
