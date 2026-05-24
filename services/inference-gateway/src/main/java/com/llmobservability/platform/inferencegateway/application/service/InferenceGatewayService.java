package com.llmobservability.platform.inferencegateway.application.service;

import com.llmobservability.platform.inferencegateway.application.port.in.CancelInferenceCommand;
import com.llmobservability.platform.inferencegateway.application.port.in.CancelInferenceResult;
import com.llmobservability.platform.inferencegateway.application.port.in.CancelConversationStreamCommand;
import com.llmobservability.platform.inferencegateway.application.port.in.ConversationMessageResult;
import com.llmobservability.platform.inferencegateway.application.port.in.ConversationMetadataResult;
import com.llmobservability.platform.inferencegateway.application.port.in.ConversationTimelineEventResult;
import com.llmobservability.platform.inferencegateway.application.port.in.ContinueConversationCommand;
import com.llmobservability.platform.inferencegateway.application.port.in.ErrorSummary;
import com.llmobservability.platform.inferencegateway.application.port.in.GetConversationQuery;
import com.llmobservability.platform.inferencegateway.application.port.in.GetInferenceStatusQuery;
import com.llmobservability.platform.inferencegateway.application.port.in.InferenceGatewayUseCase;
import com.llmobservability.platform.inferencegateway.application.port.in.InferenceStatusResult;
import com.llmobservability.platform.inferencegateway.application.port.in.ListConversationEventsQuery;
import com.llmobservability.platform.inferencegateway.application.port.in.ListConversationMessagesQuery;
import com.llmobservability.platform.inferencegateway.application.port.in.ListConversationsQuery;
import com.llmobservability.platform.inferencegateway.application.port.in.PagedResult;
import com.llmobservability.platform.inferencegateway.application.port.in.StartInferenceCommand;
import com.llmobservability.platform.inferencegateway.application.port.in.StreamConversationEventsQuery;
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
import com.llmobservability.platform.inferencegateway.application.port.out.PiiRedactionPort;
import com.llmobservability.platform.inferencegateway.application.port.out.PiiRedactionResult;
import com.llmobservability.platform.inferencegateway.application.port.out.ProviderClient;
import com.llmobservability.platform.inferencegateway.application.port.out.ProviderClientRegistry;
import com.llmobservability.platform.inferencegateway.application.service.context.ContextOrchestrationResult;
import com.llmobservability.platform.inferencegateway.application.service.context.ContextOrchestrator;
import com.llmobservability.platform.inferencegateway.application.service.context.ContextProgressEvent;
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
import com.llmobservability.platform.inferencegateway.application.port.in.ModelCatalogResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

@Service
public class InferenceGatewayService implements InferenceGatewayUseCase {
    private static final int DEFAULT_PAGE_LIMIT = 50;
    private static final int MAX_PAGE_LIMIT = 200;
    private static final Logger log = LoggerFactory.getLogger(InferenceGatewayService.class);

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
    private final ConversationContextAssembler contextAssembler;
    private final ContextOrchestrator contextOrchestrator;
    private final PiiRedactionPort piiRedactionPort;
    private final ConversationTitlePolicy titlePolicy;
    private final InferenceGatewayProperties properties;
    private final MeterRegistry meterRegistry;
    private final Optional<TransactionalOperator> transactionalOperator;

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
            ConversationContextAssembler contextAssembler,
            ContextOrchestrator contextOrchestrator,
            PiiRedactionPort piiRedactionPort,
            ConversationTitlePolicy titlePolicy,
            InferenceGatewayProperties properties,
            MeterRegistry meterRegistry
    ) {
        this(
                conversationRepository,
                conversationMessageRepository,
                inferenceRequestRepository,
                inferenceUsageRepository,
                inferenceErrorRepository,
                inferenceCancellationRepository,
                modelCatalogRepository,
                activeStreamStateStore,
                providerClientRegistry,
                lifecycleEventPublisher,
                contextAssembler,
                contextOrchestrator,
                piiRedactionPort,
                titlePolicy,
                properties,
                meterRegistry,
                Optional.empty());
    }

    @Autowired
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
            ConversationContextAssembler contextAssembler,
            ContextOrchestrator contextOrchestrator,
            PiiRedactionPort piiRedactionPort,
            ConversationTitlePolicy titlePolicy,
            InferenceGatewayProperties properties,
            MeterRegistry meterRegistry,
            Optional<TransactionalOperator> transactionalOperator
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
        this.contextAssembler = contextAssembler;
        this.contextOrchestrator = contextOrchestrator;
        this.piiRedactionPort = piiRedactionPort;
        this.titlePolicy = titlePolicy;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
        this.transactionalOperator = transactionalOperator;
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
                        .switchIfEmpty(Mono.defer(() -> prepareStream(command)).flatMapMany(prepared -> streamPrepared(prepared, execution(command, prepared.redactedMessages())))))
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
    public Mono<PagedResult<ConversationMetadataResult>> listConversations(ListConversationsQuery query) {
        int limit = normalizeLimit(query.limit());
        Instant beforeUpdatedAt = CursorCodec.decodeInstant("conversation", query.cursor());
        return conversationRepository.findByTenantProject(query.tenantId(), query.projectId(), query.status(), beforeUpdatedAt, limit + 1)
                .flatMap(this::toMetadataResult)
                .collectList()
                .map(items -> page(items, limit, item -> CursorCodec.encode("conversation", item.updatedAt().toString())));
    }

    @Override
    public Mono<ConversationMetadataResult> getConversation(GetConversationQuery query) {
        return validateConversationAccess(query.conversationId(), query.tenantId(), query.projectId())
                .flatMap(this::toMetadataResult);
    }

    @Override
    public Mono<PagedResult<ConversationMessageResult>> listConversationMessages(ListConversationMessagesQuery query) {
        int limit = normalizeLimit(query.limit());
        int afterSequence = CursorCodec.decodeInt("message", query.after(), -1);
        return validateConversationAccess(query.conversationId(), query.tenantId(), query.projectId())
                .thenMany(conversationMessageRepository.findByConversationIdAfterSequence(query.conversationId(), afterSequence, limit + 1))
                .map(this::toMessageResult)
                .collectList()
                .map(items -> page(items, limit, ConversationMessageResult::cursor));
    }

    @Override
    public Mono<PagedResult<ConversationTimelineEventResult>> listConversationEvents(ListConversationEventsQuery query) {
        int limit = normalizeLimit(query.limit());
        String afterSortKey = CursorCodec.decode("timeline", query.after());
        return validateConversationAccess(query.conversationId(), query.tenantId(), query.projectId())
                .then(loadTimeline(query.conversationId()))
                .map(events -> events.stream()
                        .filter(event -> afterSortKey == null || CursorCodec.decode("timeline", event.cursor()).compareTo(afterSortKey) > 0)
                        .limit(limit + 1L)
                        .toList())
                .map(items -> page(items, limit, ConversationTimelineEventResult::cursor));
    }

    @Override
    public Flux<StreamEvent> streamConversationEvents(StreamConversationEventsQuery query) {
        return validateConversationAccess(query.conversationId(), query.tenantId(), query.projectId())
                .thenMany(activeStreamStateStore.findActiveRequestId(query.conversationId())
                        .flatMapMany(activeRequestId -> followActiveStreamEvents(query.conversationId(), query.after()))
                        .switchIfEmpty(loadTimeline(query.conversationId()).flatMapMany(events -> Flux.fromIterable(events)
                                .map(this::toStreamEvent))));
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
                            .onErrorResume(error -> {
                                log.warn("active_stream.cancel_marker.failed requestId={} errorType={}", request.id(), error.getClass().getSimpleName());
                                return Mono.empty();
                            })
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
                                return transactional(inferenceCancellationRepository.save(cancellation)
                                        .then(inferenceRequestRepository.markCancelled(request.id(), now))
                                        .flatMap(updated -> updated
                                                ? Mono.<Void>empty()
                                                : Mono.error(new TerminalTransitionSkippedException(request.id(), InferenceStatus.CANCELLED))))
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
                                                providerResult.succeeded()))
                                        .onErrorResume(TerminalTransitionSkippedException.class, ignored -> Mono.error(new ApplicationException(
                                                ErrorCode.STREAM_CANCELLED,
                                                FailureStage.STREAMING,
                                                "Inference request is not active")));
                            });
                })
                .doOnSuccess(result -> Counter.builder("inference_stream_cancellations_total")
                        .description("Accepted inference stream cancellations")
                        .register(meterRegistry)
                        .increment());
    }

    @Override
    public Mono<CancelInferenceResult> cancelConversationStream(CancelConversationStreamCommand command) {
        return validateConversationAccess(command.conversationId(), command.tenantId(), command.projectId())
                .then(inferenceRequestRepository.findActiveByConversationId(command.conversationId()))
                .switchIfEmpty(Mono.error(new ApplicationException(
                        ErrorCode.STREAM_CANCELLED,
                        FailureStage.STREAMING,
                        "Conversation has no active stream")))
                .flatMap(request -> cancel(new CancelInferenceCommand(request.id(), command.requestedBy(), command.reason())));
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

    @Override
    public Flux<ModelCatalogResult> listModels() {
        return modelCatalogRepository.findAllEnabled()
                .map(entry -> new ModelCatalogResult(
                        entry.providerKey(),
                        entry.providerDisplayName(),
                        entry.modelKey(),
                        entry.modelDisplayName(),
                        entry.contextWindowTokens(),
                        entry.maxOutputTokens(),
                        entry.providerSupportsStreaming(),
                        entry.providerSupportsCancellation()
                ));
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
                .flatMap(model -> {
                    List<ConversationMessage> redactedMessages = toConversationMessages(command, conversationId, now);
                    return transactional(conversationRepository.save(conversation)
                            .then(conversationMessageRepository.saveAll(redactedMessages))
                            .then(Mono.just(createRequest(command, requestId, conversationId, model, redisStreamKey, now)))
                            .flatMap(inferenceRequestRepository::save))
                            .onErrorMap(this::setupConflict)
                            .flatMap(request -> activeStreamStateStore.register(request.id(), request.conversationId(), properties.activeStreamTtl())
                                    .onErrorResume(error -> {
                                        log.warn("active_stream.register.failed requestId={} errorType={}", request.id(), error.getClass().getSimpleName());
                                        return Mono.empty();
                                    })
                                    .then(publish("inference.requested", request, command.traceId(), requestedPayload(request)))
                                    .then(providerClientRegistry.get(command.provider()))
                                    .map(providerClient -> PreparedStream.create(request, model, providerClient, redactedMessages)));
                });
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
                    return inferenceRequestRepository.findActiveByConversationId(conversation.id())
                            .flatMap(activeRequest -> Mono.<PreparedContinuation>error(new ApplicationException(
                                    ErrorCode.VALIDATION_INVALID_REQUEST,
                                    FailureStage.VALIDATION,
                                    "Conversation already has an active stream")))
                            .switchIfEmpty(conversationMessageRepository.findByConversationId(conversation.id())
                                    .collectList()
                                    .flatMap(existingMessages -> prepareContinuation(command, requestId, now, redisStreamKey, existingMessages)));
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
        providerMessages.addAll(newMessages.stream()
                .map(message -> new StartInferenceCommand.Message(message.role(), message.content()))
                .toList());

        return modelCatalogRepository.findEnabledModel(command.provider(), command.model())
                .switchIfEmpty(Mono.error(new ApplicationException(
                        ErrorCode.PROVIDER_UNSUPPORTED,
                        FailureStage.VALIDATION,
                        "Model is not enabled for provider")))
                .flatMap(model -> transactional(conversationMessageRepository.saveAll(newMessages)
                        .then(Mono.just(createRequest(command, requestId, model, redisStreamKey, providerMessages, now)))
                        .flatMap(inferenceRequestRepository::save))
                        .onErrorMap(this::setupConflict)
                        .flatMap(request -> activeStreamStateStore.register(request.id(), request.conversationId(), properties.activeStreamTtl())
                                .onErrorResume(error -> {
                                    log.warn("active_stream.register.failed requestId={} errorType={}", request.id(), error.getClass().getSimpleName());
                                    return Mono.empty();
                                })
                                .then(publish("inference.requested", request, command.traceId(), requestedPayload(request)))
                                .then(providerClientRegistry.get(command.provider()))
                                .map(providerClient -> new PreparedContinuation(
                                        PreparedStream.create(request, model, providerClient, newMessages),
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
                contextAssembler.assemble(request, prepared.model(), execution.providerMessages(), execution.parameters())
                        .flatMap(assembly -> contextOrchestrator.orchestrate(request, assembly.providerMessages()))
                        .flatMapMany(context -> Flux.concat(
                                Flux.fromIterable(context.progressEvents())
                                        .map(progress -> contextEvent(progress, request, sequence.incrementAndGet(), execution.traceId())),
                                providerClient.stream(toProviderRequest(request, execution.withProviderMessages(context.providerMessages())))
                                        .flatMap(chunk -> activeStreamStateStore.cancellationRequested(request.id())
                                                .onErrorReturn(false)
                                                .flatMap(cancelled -> {
                                                    if (Boolean.TRUE.equals(cancelled)) {
                                                        return Mono.error(new ApplicationException(
                                                                ErrorCode.STREAM_CANCELLED,
                                                                FailureStage.STREAMING,
                                                                "Inference stream was cancelled"));
                                                    }
                                                    return Mono.just(chunk);
                                                }))
                                        .concatMap(chunk -> {
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
                                        })
                        )),
                Mono.defer(() -> completeRequest(request, assistantContent.toString(), inputTokens.get(), outputTokens.get(), sequence, execution.traceId()))
        );

        return lifecycle
                .publish(shared -> Flux.merge(shared, heartbeat(request, execution.traceId(), sequence).takeUntilOther(shared.ignoreElements())))
                .concatMap(event -> activeStreamStateStore.appendEvent(request.id(), request.conversationId(), event, properties.activeStreamTtl())
                        .onErrorResume(error -> Mono.empty())
                        .thenReturn(event))
                .onErrorResume(error -> {
                    if (error instanceof ApplicationException applicationException
                            && applicationException.errorCode() == ErrorCode.STREAM_CANCELLED) {
                        return cancelStream(request, assistantContent.toString(), sequence, execution.traceId());
                    }
                    return failRequest(request, assistantContent.toString(), error, sequence, execution.traceId());
                })
                .doFinally(signalType -> cleanupStream(request, assistantContent.toString(), execution.traceId(), signalType).subscribe());
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
                TokenEstimator.estimate(assistantContent),
                RedactionState.NONE,
                Map.of("source", "gemini", "partial", "false"),
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

        return transactional(conversationMessageRepository.save(assistantMessage)
                .then(inferenceUsageRepository.save(usage))
                .then(inferenceRequestRepository.markCompleted(request.id(), outputHash, now))
                .flatMap(updated -> updated
                        ? Mono.<Void>empty()
                        : Mono.error(new TerminalTransitionSkippedException(request.id(), InferenceStatus.COMPLETED))))
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
                        "totalTokens", inputTokens + outputTokens)))
                .onErrorResume(TerminalTransitionSkippedException.class, ignored -> Mono.empty());
    }

    private Flux<StreamEvent> cancelStream(InferenceRequest request, String assistantContent, AtomicLong sequence, String traceId) {
        Instant now = Instant.now();
        return transactional(persistPartialAssistantMessage(request, assistantContent, "cancelled", now)
                .then(inferenceRequestRepository.markCancelled(request.id(), now))
                .flatMap(updated -> updated
                        ? Mono.<Void>empty()
                        : Mono.error(new TerminalTransitionSkippedException(request.id(), InferenceStatus.CANCELLED))))
                .then(publish("inference.cancelled", request, traceId, Map.of(
                        "requestId", request.id().toString(),
                        "conversationId", request.conversationId().toString(),
                        "provider", request.providerKey(),
                        "model", request.modelKey(),
                        "status", InferenceStatus.CANCELLED.name())))
                .thenMany(Flux.just(event(StreamEventType.REQUEST_CANCELLED, request, sequence.incrementAndGet(), traceId, Map.of(
                        "status", InferenceStatus.CANCELLED.name()))))
                .onErrorResume(TerminalTransitionSkippedException.class, ignored -> Flux.empty());
    }

    private Flux<StreamEvent> failRequest(InferenceRequest request, String assistantContent, Throwable throwable, AtomicLong sequence, String traceId) {
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
        return transactional(inferenceErrorRepository.save(error)
                .then(persistPartialAssistantMessage(request, assistantContent, "failed", now))
                .then(inferenceRequestRepository.markFailed(request.id(), now))
                .flatMap(updated -> updated
                        ? Mono.<Void>empty()
                        : Mono.error(new TerminalTransitionSkippedException(request.id(), InferenceStatus.FAILED))))
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
                        "message", exception.getMessage()))))
                .onErrorResume(TerminalTransitionSkippedException.class, ignored -> Flux.empty());
    }

    private Mono<Void> persistPartialAssistantMessage(InferenceRequest request, String assistantContent, String terminalState, Instant now) {
        if (assistantContent == null || assistantContent.isBlank()) {
            return Mono.empty();
        }
        ConversationMessage assistantMessage = new ConversationMessage(
                UUID.randomUUID(),
                request.conversationId(),
                MessageRole.ASSISTANT,
                request.inputMessageCount(),
                assistantContent,
                ContentHasher.sha256(assistantContent),
                TokenEstimator.estimate(assistantContent),
                RedactionState.NONE,
                Map.of("source", "gemini", "partial", "true", "terminalState", terminalState),
                now);
        return conversationMessageRepository.save(assistantMessage);
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
            if (message.role() == MessageRole.USER) {
                RedactedMessage redacted = redactUserMessage(message.content(), conversationId, i);
                messages.add(new ConversationMessage(
                        UUID.randomUUID(),
                        conversationId,
                        message.role(),
                        i,
                        redacted.content(),
                        ContentHasher.sha256(redacted.content()),
                        TokenEstimator.estimate(redacted.content()),
                        redacted.state(),
                        redacted.metadata(),
                        now));
            } else {
                messages.add(new ConversationMessage(
                        UUID.randomUUID(),
                        conversationId,
                        message.role(),
                        i,
                        message.content(),
                        ContentHasher.sha256(message.content()),
                        TokenEstimator.estimate(message.content()),
                        RedactionState.NONE,
                        Map.of("source", "request"),
                        now));
            }
        }
        return messages;
    }

    private List<ConversationMessage> toConversationMessages(ContinueConversationCommand command, int startingSequence, Instant now) {
        List<ConversationMessage> messages = new ArrayList<>();
        for (int i = 0; i < command.messages().size(); i++) {
            StartInferenceCommand.Message message = command.messages().get(i);
            if (message.role() == MessageRole.USER) {
                RedactedMessage redacted = redactUserMessage(message.content(), command.conversationId(), startingSequence + i);
                messages.add(new ConversationMessage(
                        UUID.randomUUID(),
                        command.conversationId(),
                        message.role(),
                        startingSequence + i,
                        redacted.content(),
                        ContentHasher.sha256(redacted.content()),
                        TokenEstimator.estimate(redacted.content()),
                        redacted.state(),
                        redacted.metadata(),
                        now));
            } else {
                messages.add(new ConversationMessage(
                        UUID.randomUUID(),
                        command.conversationId(),
                        message.role(),
                        startingSequence + i,
                        message.content(),
                        ContentHasher.sha256(message.content()),
                        TokenEstimator.estimate(message.content()),
                        RedactionState.NONE,
                        Map.of("source", "request"),
                        now));
            }
        }
        return messages;
    }

    /**
     * Scans a user message for PII and returns the (possibly redacted) content,
     * the appropriate {@link RedactionState}, and metadata.
     *
     * <p>If the redaction adapter throws, the error is logged and the original
     * content is returned unchanged with {@code RedactionState.NONE} so that
     * inference is never blocked by a redaction failure.
     *
     * <p>Raw matched values are never logged.
     */
    private RedactedMessage redactUserMessage(String content, UUID conversationId, int sequence) {
        try {
            Timer.Sample sample = Timer.start(meterRegistry);
            PiiRedactionResult result = piiRedactionPort.scan(content);
            sample.stop(Timer.builder("pii_redaction_latency_seconds")
                    .description("Time taken to scan a user message for PII")
                    .register(meterRegistry));

            if (result.hasRedactions()) {
                for (String category : result.detectedCategories()) {
                    Counter.builder("pii_redaction_triggered_total")
                            .description("Number of user messages where PII was detected and redacted")
                            .tag("category", category)
                            .register(meterRegistry)
                            .increment();
                }
                log.info("audit.pii.redacted conversationId={} sequence={} categories={}",
                        conversationId, sequence, result.detectedCategories());

                Map<String, String> metadata = new LinkedHashMap<>();
                metadata.put("source", "request");
                metadata.put("redactedCategories", result.detectedCategories().toString());
                return new RedactedMessage(result.redactedContent(), RedactionState.REDACTED, Map.copyOf(metadata));
            }
        } catch (Exception ex) {
            log.error("pii.redaction.error conversationId={} sequence={} error={}",
                    conversationId, sequence, ex.getMessage());
        }
        return new RedactedMessage(content, RedactionState.NONE, Map.of("source", "request"));
    }

    /** Value type holding the outcome of a per-message redaction pass. */
    private record RedactedMessage(String content, RedactionState state, Map<String, String> metadata) {}

    private ProviderClient.ProviderRequest toProviderRequest(InferenceRequest request, StreamExecution execution) {
        List<ProviderClient.ProviderMessage> messages = execution.providerMessages().stream()
                .map(message -> new ProviderClient.ProviderMessage(message.role().name().toLowerCase(), message.content()))
                .toList();
        return new ProviderClient.ProviderRequest(request.id(), request.modelKey(), messages, execution.parameters());
    }

    private StreamExecution execution(StartInferenceCommand command, List<ConversationMessage> redactedMessages) {
        List<StartInferenceCommand.Message> providerMessages = redactedMessages.stream()
                .map(m -> new StartInferenceCommand.Message(m.role(), m.content()))
                .toList();
        return new StreamExecution(command.parameters(), command.traceId(), providerMessages, "created");
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
                payload)
                .onErrorResume(error -> {
                    log.warn("lifecycle_event.publish.failed eventName={} requestId={} errorType={}",
                            eventName, request.id(), error.getClass().getSimpleName());
                    return Mono.empty();
                });
    }

    private Mono<Void> cleanupStream(InferenceRequest request, String assistantContent, String traceId, SignalType signalType) {
        Mono<Void> terminalCleanup = Mono.empty();
        if (signalType == SignalType.CANCEL) {
            Instant now = Instant.now();
            terminalCleanup = transactional(persistPartialAssistantMessage(request, assistantContent, "client_disconnected", now)
                    .then(inferenceRequestRepository.markCancelled(request.id(), now))
                    .flatMap(updated -> updated
                            ? Mono.<Void>empty()
                            : Mono.error(new TerminalTransitionSkippedException(request.id(), InferenceStatus.CANCELLED))))
                    .then(publish("inference.cancelled", request, traceId, Map.of(
                            "requestId", request.id().toString(),
                            "conversationId", request.conversationId().toString(),
                            "provider", request.providerKey(),
                            "model", request.modelKey(),
                            "status", InferenceStatus.CANCELLED.name(),
                            "reason", "client_disconnected")))
                    .onErrorResume(TerminalTransitionSkippedException.class, ignored -> Mono.empty());
        }
        return terminalCleanup
                .then(activeStreamStateStore.clear(request.id())
                        .onErrorResume(error -> {
                            log.warn("active_stream.clear.failed requestId={} errorType={}", request.id(), error.getClass().getSimpleName());
                            return Mono.empty();
                        }));
    }

    private <T> Mono<T> transactional(Mono<T> mono) {
        return transactionalOperator
                .map(operator -> operator.transactional(mono))
                .orElse(mono);
    }

    private Throwable setupConflict(Throwable error) {
        if (error instanceof DuplicateKeyException || error instanceof DataIntegrityViolationException) {
            return new ApplicationException(
                    ErrorCode.VALIDATION_INVALID_REQUEST,
                    FailureStage.VALIDATION,
                    null,
                    "Request setup conflicted with an existing idempotency key or active stream",
                    false,
                    error);
        }
        return error;
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

    private StreamEvent contextEvent(ContextProgressEvent progress, InferenceRequest request, long sequence, String traceId) {
        Map<String, Object> data = new LinkedHashMap<>(progress.data());
        data.putIfAbsent("status", "context");
        return event(progress.type(), request, sequence, traceId, data);
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

    private Mono<Conversation> validateConversationAccess(UUID conversationId, String tenantId, String projectId) {
        return conversationRepository.findById(conversationId)
                .filter(conversation -> conversation.tenantId().equals(tenantId) && conversation.projectId().equals(projectId))
                .switchIfEmpty(Mono.error(new ApplicationException(
                        ErrorCode.CONVERSATION_NOT_FOUND,
                        FailureStage.VALIDATION,
                        "Conversation was not found")));
    }

    private Mono<ConversationMetadataResult> toMetadataResult(Conversation conversation) {
        return Mono.zip(
                        conversationMessageRepository.countByConversationId(conversation.id()).defaultIfEmpty(0L),
                        conversationMessageRepository.findLatestByConversationId(conversation.id()).map(ConversationMessage::createdAt).defaultIfEmpty(conversation.updatedAt()),
                        inferenceRequestRepository.findActiveByConversationId(conversation.id()).map(InferenceRequest::id).defaultIfEmpty(nullUuid()),
                        inferenceRequestRepository.findLatestByConversationId(conversation.id()).map(InferenceRequest::status).defaultIfEmpty(InferenceStatus.ACCEPTED)
                )
                .map(tuple -> new ConversationMetadataResult(
                        conversation.id(),
                        conversation.tenantId(),
                        conversation.projectId(),
                        conversation.status(),
                        conversation.title(),
                        conversation.titleSource(),
                        conversation.createdAt(),
                        conversation.updatedAt(),
                        conversation.cancelledAt(),
                        tuple.getT2(),
                        tuple.getT1(),
                        nullUuid().equals(tuple.getT3()) ? null : tuple.getT3(),
                        tuple.getT4()));
    }

    private ConversationMessageResult toMessageResult(ConversationMessage message) {
        return new ConversationMessageResult(
                message.id(),
                message.conversationId(),
                message.role(),
                message.sequence(),
                message.content(),
                message.contentHash(),
                message.estimatedTokens(),
                message.redactionState(),
                message.metadata(),
                message.createdAt(),
                CursorCodec.encode("message", Integer.toString(message.sequence())));
    }

    private Mono<List<ConversationTimelineEventResult>> loadTimeline(UUID conversationId) {
        Mono<Conversation> conversation = conversationRepository.findById(conversationId);
        Mono<List<ConversationMessage>> messages = conversationMessageRepository.findByConversationId(conversationId).collectList();
        Mono<List<InferenceRequest>> requests = inferenceRequestRepository.findByConversationId(conversationId).collectList();
        return Mono.zip(conversation, messages, requests)
                .flatMap(tuple -> Flux.concat(
                                Flux.just(conversationCreatedTimelineEvent(tuple.getT1())),
                                Flux.fromIterable(tuple.getT2()).map(this::messageTimelineEvent),
                                Flux.fromIterable(tuple.getT3()).flatMap(this::requestTimelineEvents)
                        )
                        .sort(Comparator.comparing(event -> CursorCodec.decode("timeline", event.cursor())))
                        .collectList());
    }

    private ConversationTimelineEventResult conversationCreatedTimelineEvent(Conversation conversation) {
        return timelineEvent(
                "conversation.created:" + conversation.id(),
                "conversation.created",
                conversation.id(),
                null,
                null,
                0,
                conversation.createdAt(),
                0,
                Map.of(
                        "status", conversation.status().name(),
                        "title", conversation.title(),
                        "titleSource", conversation.titleSource()));
    }

    private ConversationTimelineEventResult messageTimelineEvent(ConversationMessage message) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("role", message.role().name());
        data.put("sequence", message.sequence());
        data.put("content", message.content());
        data.put("contentHash", message.contentHash());
        data.put("estimatedTokens", message.estimatedTokens());
        data.put("redactionState", message.redactionState().name());
        data.put("metadata", message.metadata());
        String id = "message:" + message.id();
        return timelineEvent(id, "conversation.message", message.conversationId(), null, message.id(), message.sequence(), message.createdAt(), 20, data);
    }

    private Flux<ConversationTimelineEventResult> requestTimelineEvents(InferenceRequest request) {
        Flux<ConversationTimelineEventResult> lifecycle = Flux.fromIterable(requestLifecycleEvents(request));
        Flux<ConversationTimelineEventResult> usage = inferenceUsageRepository.findByRequestId(request.id())
                .map(value -> usageTimelineEvent(request, value))
                .flux();
        Flux<ConversationTimelineEventResult> error = inferenceErrorRepository.findLatest(request.id())
                .map(value -> errorTimelineEvent(request, value))
                .flux();
        Flux<ConversationTimelineEventResult> cancellation = inferenceCancellationRepository.findLatest(request.id())
                .map(value -> cancellationTimelineEvent(request, value))
                .flux();
        return Flux.concat(lifecycle, usage, error, cancellation);
    }

    private List<ConversationTimelineEventResult> requestLifecycleEvents(InferenceRequest request) {
        List<ConversationTimelineEventResult> events = new ArrayList<>();
        events.add(requestTimelineEvent(request, "request.accepted", request.createdAt(), 10, Map.of(
                "status", InferenceStatus.ACCEPTED.name(),
                "provider", request.providerKey(),
                "model", request.modelKey(),
                "inputMessageCount", request.inputMessageCount(),
                "inputContentHash", request.inputContentHash())));
        if (request.firstTokenAt() != null) {
            events.add(requestTimelineEvent(request, "request.streaming", request.firstTokenAt(), 30, Map.of(
                    "status", InferenceStatus.STREAMING.name())));
        }
        if (request.completedAt() != null) {
            events.add(requestTimelineEvent(request, "request.completed", request.completedAt(), 40, Map.of(
                    "status", InferenceStatus.COMPLETED.name(),
                    "outputContentHash", request.outputContentHash())));
        }
        if (request.cancelledAt() != null) {
            events.add(requestTimelineEvent(request, "request.cancelled", request.cancelledAt(), 50, Map.of(
                    "status", InferenceStatus.CANCELLED.name())));
        }
        if (request.failedAt() != null) {
            events.add(requestTimelineEvent(request, "request.failed", request.failedAt(), 60, Map.of(
                    "status", InferenceStatus.FAILED.name())));
        }
        return events;
    }

    private ConversationTimelineEventResult usageTimelineEvent(InferenceRequest request, InferenceUsage usage) {
        return requestTimelineEvent(request, "usage.recorded", usage.createdAt(), 45, Map.of(
                "inputTokens", usage.inputTokens(),
                "outputTokens", usage.outputTokens(),
                "totalTokens", usage.totalTokens(),
                "providerReportedUnits", usage.providerReportedUnits(),
                "estimatedCostAmount", usage.estimatedCostAmount(),
                "estimatedCostCurrency", usage.estimatedCostCurrency()));
    }

    private ConversationTimelineEventResult errorTimelineEvent(InferenceRequest request, InferenceError error) {
        return requestTimelineEvent(request, "request.error", error.createdAt(), 65, Map.of(
                "failureStage", error.failureStage().name(),
                "errorCode", error.errorCode(),
                "message", error.message(),
                "retryable", error.retryable()));
    }

    private ConversationTimelineEventResult cancellationTimelineEvent(InferenceRequest request, InferenceCancellation cancellation) {
        return requestTimelineEvent(request, "request.cancellation", cancellation.createdAt(), 55, Map.of(
                "requestedBy", cancellation.requestedBy(),
                "reason", cancellation.reason(),
                "providerCancellationAttempted", cancellation.providerCancellationAttempted(),
                "providerCancellationSucceeded", cancellation.providerCancellationSucceeded()));
    }

    private ConversationTimelineEventResult requestTimelineEvent(InferenceRequest request, String type, Instant occurredAt, int rank, Map<String, Object> data) {
        return timelineEvent(
                type + ":" + request.id(),
                type,
                request.conversationId(),
                request.id(),
                null,
                rank,
                occurredAt,
                rank,
                data);
    }

    private ConversationTimelineEventResult timelineEvent(
            String id,
            String type,
            UUID conversationId,
            UUID requestId,
            UUID messageId,
            long sequence,
            Instant occurredAt,
            int rank,
            Map<String, Object> data
    ) {
        String sortKey = occurredAt.toString() + "|" + String.format("%03d", rank) + "|" + id;
        return new ConversationTimelineEventResult(
                id,
                type,
                conversationId,
                requestId,
                messageId,
                sequence,
                occurredAt,
                data,
                CursorCodec.encode("timeline", sortKey));
    }

    private StreamEvent toStreamEvent(ConversationTimelineEventResult event) {
        return new StreamEvent(
                event.id(),
                StreamEventType.MESSAGE_DELTA,
                event.requestId(),
                event.conversationId(),
                null,
                event.sequence(),
                event.occurredAt(),
                Map.of("type", event.type(), "timeline", event.data()));
    }

    private Flux<StreamEvent> followActiveStreamEvents(UUID conversationId, String afterEventId) {
        AtomicReference<String> lastSeenEventId = new AtomicReference<>(afterEventId);
        Flux<StreamEvent> replay = activeStreamStateStore.replayEvents(conversationId, afterEventId)
                .doOnNext(event -> lastSeenEventId.set(event.id()));
        Flux<StreamEvent> follow = Flux.interval(properties.streamHeartbeat())
                .flatMap(tick -> activeStreamStateStore.findActiveRequestId(conversationId)
                        .flatMapMany(activeRequestId -> activeStreamStateStore.replayEvents(conversationId, lastSeenEventId.get())
                                .doOnNext(event -> lastSeenEventId.set(event.id()))));
        return Flux.concat(replay, follow);
    }

    private <T> PagedResult<T> page(List<T> values, int limit, Function<T, String> cursorExtractor) {
        boolean hasMore = values.size() > limit;
        List<T> items = hasMore ? values.subList(0, limit) : values;
        String nextCursor = hasMore && !items.isEmpty() ? cursorExtractor.apply(items.getLast()) : null;
        return new PagedResult<>(items, nextCursor);
    }

    private int normalizeLimit(int requestedLimit) {
        if (requestedLimit <= 0) {
            return DEFAULT_PAGE_LIMIT;
        }
        return Math.min(requestedLimit, MAX_PAGE_LIMIT);
    }

    private UUID nullUuid() {
        return new UUID(0L, 0L);
    }

    private InferenceUsage emptyUsage(UUID requestId) {
        return new InferenceUsage(null, null, 0, 0, 0, null, null, null, null);
    }

    private InferenceError emptyError(UUID requestId) {
        return new InferenceError(null, null, null, null, null, null, false, null);
    }

    private record PreparedStream(InferenceRequest request, ModelCatalogEntry model, ProviderClient providerClient, List<ConversationMessage> redactedMessages) {
        private static PreparedStream create(InferenceRequest request, ModelCatalogEntry model, ProviderClient providerClient, List<ConversationMessage> redactedMessages) {
            return new PreparedStream(request, model, providerClient, redactedMessages);
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
        StreamExecution withProviderMessages(List<StartInferenceCommand.Message> nextProviderMessages) {
            return new StreamExecution(parameters, traceId, nextProviderMessages, conversationState);
        }
    }

    private static final class TerminalTransitionSkippedException extends RuntimeException {
        TerminalTransitionSkippedException(UUID requestId, InferenceStatus targetStatus) {
            super("Terminal transition to " + targetStatus + " skipped for request " + requestId);
        }
    }

    private static final class CursorCodec {
        private CursorCodec() {
        }

        static String encode(String namespace, String value) {
            String payload = namespace + ":v1:" + value;
            return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        }

        static String decode(String namespace, String cursor) {
            if (cursor == null || cursor.isBlank()) {
                return null;
            }
            String decoded;
            try {
                decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException exception) {
                throw new ApplicationException(
                        ErrorCode.VALIDATION_INVALID_REQUEST,
                        FailureStage.VALIDATION,
                        "Invalid cursor");
            }
            String prefix = namespace + ":v1:";
            if (!decoded.startsWith(prefix)) {
                throw new ApplicationException(
                        ErrorCode.VALIDATION_INVALID_REQUEST,
                        FailureStage.VALIDATION,
                        "Invalid cursor");
            }
            return decoded.substring(prefix.length());
        }

        static int decodeInt(String namespace, String cursor, int defaultValue) {
            String decoded = decode(namespace, cursor);
            return decoded == null ? defaultValue : Integer.parseInt(decoded);
        }

        static Instant decodeInstant(String namespace, String cursor) {
            String decoded = decode(namespace, cursor);
            return decoded == null ? null : Instant.parse(decoded);
        }
    }
}
