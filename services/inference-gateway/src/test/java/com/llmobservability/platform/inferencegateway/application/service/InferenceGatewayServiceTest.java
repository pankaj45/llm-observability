package com.llmobservability.platform.inferencegateway.application.service;

import com.llmobservability.platform.inferencegateway.application.port.in.CancelInferenceCommand;
import com.llmobservability.platform.inferencegateway.application.port.in.ConversationMessageResult;
import com.llmobservability.platform.inferencegateway.application.port.in.GetConversationQuery;
import com.llmobservability.platform.inferencegateway.application.port.in.ListConversationEventsQuery;
import com.llmobservability.platform.inferencegateway.application.port.in.ListConversationMessagesQuery;
import com.llmobservability.platform.inferencegateway.application.port.in.ContinueConversationCommand;
import com.llmobservability.platform.inferencegateway.application.port.in.StartInferenceCommand;
import com.llmobservability.platform.inferencegateway.application.port.out.ActiveStreamStateStore;
import com.llmobservability.platform.inferencegateway.application.port.out.ConversationMessageRepository;
import com.llmobservability.platform.inferencegateway.application.port.out.ConversationRepository;
import com.llmobservability.platform.inferencegateway.application.port.out.InferenceCancellationRepository;
import com.llmobservability.platform.inferencegateway.application.port.out.InferenceErrorRepository;
import com.llmobservability.platform.inferencegateway.application.port.out.InferenceRequestRepository;
import com.llmobservability.platform.inferencegateway.application.port.out.InferenceUsageRepository;
import com.llmobservability.platform.inferencegateway.application.port.out.LifecycleEventPublisher;
import com.llmobservability.platform.inferencegateway.application.port.out.ContextEvidenceCache;
import com.llmobservability.platform.inferencegateway.application.port.out.ContextToolInvocationRepository;
import com.llmobservability.platform.inferencegateway.application.port.out.ModelCatalogRepository;
import com.llmobservability.platform.inferencegateway.application.port.out.ProviderClient;
import com.llmobservability.platform.inferencegateway.application.port.out.ProviderClientRegistry;
import com.llmobservability.platform.inferencegateway.config.ContextOrchestratorProperties;
import com.llmobservability.platform.inferencegateway.config.InferenceGatewayProperties;
import com.llmobservability.platform.inferencegateway.application.service.context.ContextOrchestrator;
import com.llmobservability.platform.inferencegateway.application.service.context.RuntimeContextPolicy;
import com.llmobservability.platform.inferencegateway.application.service.context.ToolNeedRouter;
import com.llmobservability.platform.inferencegateway.domain.model.Conversation;
import com.llmobservability.platform.inferencegateway.domain.model.ConversationStatus;
import com.llmobservability.platform.inferencegateway.domain.model.ConversationMessage;
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
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class InferenceGatewayServiceTest {

    @Test
    void streamCreatesConversationAndPersistsMessages() {
        TestState state = new TestState();
        InferenceGatewayService service = service(state);

        List<StreamEvent> events = service.stream(command("phase2-stream-1")).collectList().block();

        assertThat(events).extracting(StreamEvent::type)
                .containsExactly(StreamEventType.REQUEST_ACCEPTED, StreamEventType.TOKEN_DELTA, StreamEventType.REQUEST_COMPLETED);
        assertThat(state.conversations).hasSize(1);
        assertThat(state.requests).hasSize(1);
        assertThat(state.messages).hasSize(2);

        InferenceRequest request = state.requests.values().iterator().next();
        Conversation conversation = state.conversations.values().iterator().next();
        assertThat(request.conversationId()).isEqualTo(conversation.id());
        assertThat(request.id()).isNotNull();
        assertThat(conversation.title()).isEqualTo("Explain phase two");
        assertThat(state.messages).extracting(ConversationMessage::role)
                .containsExactly(MessageRole.USER, MessageRole.ASSISTANT);
        assertThat(state.messages.getLast().content()).isEqualTo("done");
        assertThat(state.usages.values()).singleElement()
                .extracting(InferenceUsage::totalTokens)
                .isEqualTo(8);
    }

    @Test
    void continueConversationAppendsMessagesAndStreamsWithExistingContext() {
        TestState state = new TestState();
        InferenceGatewayService service = service(state);
        UUID conversationId = UUID.randomUUID();
        Instant now = Instant.now();
        state.conversations.put(conversationId, new Conversation(
                conversationId,
                "tenant-a",
                "project-a",
                ConversationStatus.ACTIVE,
                "Explain phase two",
                "FIRST_USER_MESSAGE",
                now,
                now,
                null));
        state.messages.add(new ConversationMessage(
                UUID.randomUUID(),
                conversationId,
                MessageRole.USER,
                0,
                "Explain phase two",
                "hash-1",
                5,
                RedactionState.NONE,
                Map.of("source", "request"),
                now));
        state.messages.add(new ConversationMessage(
                UUID.randomUUID(),
                conversationId,
                MessageRole.ASSISTANT,
                1,
                "done",
                "hash-2",
                1,
                RedactionState.NONE,
                Map.of("source", "gemini"),
                now));

        List<StreamEvent> events = service.continueConversation(new ContinueConversationCommand(
                conversationId,
                "tenant-a",
                "project-a",
                "gemini",
                "gemini-1.5-flash",
                List.of(new StartInferenceCommand.Message(MessageRole.USER, "Can you give an example?")),
                Map.of("temperature", 0.2),
                Map.of("purpose", "test"),
                "client-2",
                Map.of(),
                "phase2-continue-1",
                "trace-2")).collectList().block();

        assertThat(events).extracting(StreamEvent::type)
                .containsExactly(StreamEventType.REQUEST_ACCEPTED, StreamEventType.TOKEN_DELTA, StreamEventType.REQUEST_COMPLETED);
        assertThat(state.conversations).hasSize(1);
        assertThat(state.requests).hasSize(1);
        assertThat(state.requests.values().iterator().next().conversationId()).isEqualTo(conversationId);
        assertThat(state.messages).hasSize(4);
        assertThat(state.messages.get(2).role()).isEqualTo(MessageRole.USER);
        assertThat(state.messages.get(2).sequence()).isEqualTo(2);
        assertThat(state.messages.get(3).role()).isEqualTo(MessageRole.ASSISTANT);
        assertThat(state.messages.get(3).sequence()).isEqualTo(3);
        assertThat(state.providerRequests).singleElement()
                .satisfies(providerRequest -> assertThat(providerRequest.messages())
                        .extracting(ProviderClient.ProviderMessage::content)
                        .containsExactly(
                                providerRequest.messages().get(0).content(),
                                "Explain phase two",
                                "done",
                                "Can you give an example?"));
        assertThat(state.providerRequests.getFirst().messages().getFirst().role()).isEqualTo("system");
    }

    @Test
    void conversationMessagesReturnMetadataAndRawContentAfterScopeValidation() {
        TestState state = new TestState();
        InferenceGatewayService service = service(state);
        UUID conversationId = UUID.randomUUID();
        Instant now = Instant.now();
        state.conversations.put(conversationId, new Conversation(
                conversationId,
                "tenant-a",
                "project-a",
                ConversationStatus.ACTIVE,
                "Phase four",
                "FIRST_USER_MESSAGE",
                now,
                now,
                null));
        state.messages.add(new ConversationMessage(
                UUID.randomUUID(),
                conversationId,
                MessageRole.USER,
                0,
                "hello",
                "hash-user",
                2,
                RedactionState.NONE,
                Map.of("source", "request"),
                now));
        state.messages.add(new ConversationMessage(
                UUID.randomUUID(),
                conversationId,
                MessageRole.ASSISTANT,
                1,
                "hi there",
                "hash-assistant",
                2,
                RedactionState.NONE,
                Map.of("source", "gemini"),
                now.plusMillis(1)));

        StepVerifier.create(service.getConversation(new GetConversationQuery(conversationId, "tenant-a", "project-a")))
                .assertNext(result -> {
                    assertThat(result.messageCount()).isEqualTo(2);
                    assertThat(result.lastMessageAt()).isEqualTo(now.plusMillis(1));
                })
                .verifyComplete();

        StepVerifier.create(service.listConversationMessages(new ListConversationMessagesQuery(
                        conversationId, "tenant-a", "project-a", null, 50)))
                .assertNext(page -> {
                    assertThat(page.items()).extracting(ConversationMessageResult::content)
                            .containsExactly("hello", "hi there");
                    assertThat(page.nextCursor()).isNull();
                })
                .verifyComplete();
    }

    @Test
    void timelineDerivesMessageAndRequestLifecycleEventsFromCanonicalTables() {
        TestState state = new TestState();
        InferenceGatewayService service = service(state);
        UUID conversationId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        Instant now = Instant.now();
        state.conversations.put(conversationId, new Conversation(
                conversationId,
                "tenant-a",
                "project-a",
                ConversationStatus.ACTIVE,
                "Phase four",
                "FIRST_USER_MESSAGE",
                now,
                now,
                null));
        state.messages.add(new ConversationMessage(
                UUID.randomUUID(),
                conversationId,
                MessageRole.USER,
                0,
                "hello",
                "hash-user",
                2,
                RedactionState.NONE,
                Map.of("source", "request"),
                now));
        state.requests.put(requestId, new InferenceRequest(
                requestId,
                "tenant-a",
                "project-a",
                conversationId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "gemini",
                "gemini-1.5-flash",
                "timeline-test",
                InferenceStatus.COMPLETED,
                true,
                Map.of(),
                1,
                "hash-user",
                "hash-output",
                "inference:stream:" + requestId,
                now.plusMillis(1),
                now.plusMillis(1),
                now.plusMillis(2),
                now.plusMillis(3),
                null,
                null,
                now.plusMillis(3)));
        state.usages.put(requestId, new InferenceUsage(
                UUID.randomUUID(),
                requestId,
                3,
                5,
                8,
                "tokens",
                java.math.BigDecimal.ZERO,
                "USD",
                now.plusMillis(4)));

        StepVerifier.create(service.listConversationEvents(new ListConversationEventsQuery(
                        conversationId, "tenant-a", "project-a", null, 50)))
                .assertNext(page -> assertThat(page.items()).extracting(event -> event.type())
                        .containsExactly(
                                "conversation.created",
                                "conversation.message",
                                "request.accepted",
                                "request.streaming",
                                "request.completed",
                                "usage.recorded"))
                .verifyComplete();
    }

    @Test
    void continueConversationRejectsConcurrentActiveStream() {
        TestState state = new TestState();
        InferenceGatewayService service = service(state);
        UUID conversationId = UUID.randomUUID();
        Instant now = Instant.now();
        state.conversations.put(conversationId, new Conversation(
                conversationId,
                "tenant-a",
                "project-a",
                ConversationStatus.ACTIVE,
                "Busy conversation",
                "FIRST_USER_MESSAGE",
                now,
                now,
                null));
        InferenceRequest activeRequest = acceptedRequest(conversationId);
        state.requests.put(activeRequest.id(), activeRequest);

        StepVerifier.create(service.continueConversation(new ContinueConversationCommand(
                        conversationId,
                        "tenant-a",
                        "project-a",
                        "gemini",
                        "gemini-1.5-flash",
                        List.of(new StartInferenceCommand.Message(MessageRole.USER, "Can you continue?")),
                        Map.of(),
                        Map.of(),
                        "client-2",
                        Map.of(),
                        "phase4-busy",
                        "trace-2")))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ApplicationException.class)
                        .hasMessageContaining("active stream"))
                .verify();
    }

    @Test
    void cancelMarksActiveRequestAndStoresCancellation() {
        TestState state = new TestState();
        InferenceGatewayService service = service(state);
        InferenceRequest request = acceptedRequest();
        state.requests.put(request.id(), request);

        StepVerifier.create(service.cancel(new CancelInferenceCommand(request.id(), "tester", "no longer needed")))
                .assertNext(result -> {
                    assertThat(result.requestId()).isEqualTo(request.id());
                    assertThat(result.conversationId()).isEqualTo(request.conversationId());
                    assertThat(result.status()).isEqualTo(InferenceStatus.CANCELLED);
                })
                .verifyComplete();

        assertThat(state.requests.get(request.id()).status()).isEqualTo(InferenceStatus.CANCELLED);
        assertThat(state.cancellations).hasSize(1);
        assertThat(state.cancelRequested).contains(request.id());
    }

    private InferenceGatewayService service(TestState state) {
        return new InferenceGatewayService(
                state.conversationRepository(),
                state.conversationMessageRepository(),
                state.inferenceRequestRepository(),
                state.inferenceUsageRepository(),
                state.inferenceErrorRepository(),
                state.inferenceCancellationRepository(),
                state.modelCatalogRepository(),
                state.activeStreamStateStore(),
                state.providerClientRegistry(),
                state.lifecycleEventPublisher(),
                contextOrchestrator(),
                new ConversationTitlePolicy(),
                new InferenceGatewayProperties(Duration.ofHours(1), Duration.ofMinutes(10)),
                new SimpleMeterRegistry());
    }

    private ContextOrchestrator contextOrchestrator() {
        ContextOrchestratorProperties properties = new ContextOrchestratorProperties();
        properties.setEnabled(false);
        ContextEvidenceCache cache = new ContextEvidenceCache() {
            @Override
            public Mono<List<com.llmobservability.platform.inferencegateway.application.service.context.ContextEvidence>> get(String key) {
                return Mono.just(List.of());
            }

            @Override
            public Mono<Void> put(String key, List<com.llmobservability.platform.inferencegateway.application.service.context.ContextEvidence> evidence, Duration ttl) {
                return Mono.empty();
            }
        };
        ContextToolInvocationRepository ledger = invocation -> Mono.empty();
        return new ContextOrchestrator(
                new RuntimeContextPolicy(properties),
                new ToolNeedRouter(),
                query -> Mono.just(List.of()),
                (query, maxResults) -> Mono.just(List.of()),
                cache,
                ledger,
                properties,
                new SimpleMeterRegistry());
    }

    private StartInferenceCommand command(String idempotencyKey) {
        return new StartInferenceCommand(
                "tenant-a",
                "project-a",
                "gemini",
                "gemini-1.5-flash",
                List.of(new StartInferenceCommand.Message(MessageRole.USER, "Explain phase two")),
                Map.of("temperature", 0.2),
                Map.of("purpose", "test"),
                "client-1",
                Map.of(),
                idempotencyKey,
                "trace-1");
    }

    private InferenceRequest acceptedRequest() {
        return acceptedRequest(UUID.randomUUID());
    }

    private InferenceRequest acceptedRequest(UUID conversationId) {
        UUID requestId = UUID.randomUUID();
        Instant now = Instant.now();
        return new InferenceRequest(
                requestId,
                "tenant-a",
                "project-a",
                conversationId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "gemini",
                "gemini-1.5-flash",
                "cancel-test",
                InferenceStatus.ACCEPTED,
                true,
                Map.of(),
                1,
                "input-hash",
                null,
                "inference:stream:" + requestId,
                now,
                now,
                null,
                null,
                null,
                null,
                now);
    }

    private static final class TestState {
        final Map<UUID, Conversation> conversations = new ConcurrentHashMap<>();
        final List<ConversationMessage> messages = new ArrayList<>();
        final Map<UUID, InferenceRequest> requests = new ConcurrentHashMap<>();
        final Map<UUID, InferenceUsage> usages = new ConcurrentHashMap<>();
        final List<InferenceError> errors = new ArrayList<>();
        final List<InferenceCancellation> cancellations = new ArrayList<>();
        final List<ProviderClient.ProviderRequest> providerRequests = new ArrayList<>();
        final Set<UUID> cancelRequested = ConcurrentHashMap.newKeySet();

        ConversationRepository conversationRepository() {
            return new ConversationRepository() {
                @Override
                public Mono<Conversation> save(Conversation conversation) {
                    conversations.put(conversation.id(), conversation);
                    return Mono.just(conversation);
                }

                @Override
                public Mono<Conversation> findById(UUID conversationId) {
                    return Mono.justOrEmpty(conversations.get(conversationId));
                }

                @Override
                public Flux<Conversation> findByTenantProject(String tenantId, String projectId, ConversationStatus status, Instant beforeUpdatedAt, int limit) {
                    return Flux.fromIterable(conversations.values().stream()
                            .filter(conversation -> conversation.tenantId().equals(tenantId))
                            .filter(conversation -> conversation.projectId().equals(projectId))
                            .filter(conversation -> status == null || conversation.status() == status)
                            .filter(conversation -> beforeUpdatedAt == null || conversation.updatedAt().isBefore(beforeUpdatedAt))
                            .sorted((left, right) -> right.updatedAt().compareTo(left.updatedAt()))
                            .limit(limit)
                            .toList());
                }
            };
        }

        ConversationMessageRepository conversationMessageRepository() {
            return new ConversationMessageRepository() {
                @Override
                public Mono<Void> saveAll(List<ConversationMessage> newMessages) {
                    messages.addAll(newMessages);
                    return Mono.empty();
                }

                @Override
                public Mono<Void> save(ConversationMessage message) {
                    messages.add(message);
                    return Mono.empty();
                }

                @Override
                public Flux<ConversationMessage> findByConversationId(UUID conversationId) {
                    return Flux.fromIterable(messages.stream()
                            .filter(message -> message.conversationId().equals(conversationId))
                            .toList());
                }

                @Override
                public Flux<ConversationMessage> findByConversationIdAfterSequence(UUID conversationId, int afterSequence, int limit) {
                    return Flux.fromIterable(messages.stream()
                            .filter(message -> message.conversationId().equals(conversationId))
                            .filter(message -> message.sequence() > afterSequence)
                            .sorted((left, right) -> Integer.compare(left.sequence(), right.sequence()))
                            .limit(limit)
                            .toList());
                }

                @Override
                public Mono<Long> countByConversationId(UUID conversationId) {
                    return Mono.just(messages.stream()
                            .filter(message -> message.conversationId().equals(conversationId))
                            .count());
                }

                @Override
                public Mono<ConversationMessage> findLatestByConversationId(UUID conversationId) {
                    return Flux.fromIterable(messages.stream()
                                    .filter(message -> message.conversationId().equals(conversationId))
                                    .sorted((left, right) -> Integer.compare(right.sequence(), left.sequence()))
                                    .toList())
                            .next();
                }
            };
        }

        InferenceRequestRepository inferenceRequestRepository() {
            return new InferenceRequestRepository() {
                @Override
                public Mono<InferenceRequest> save(InferenceRequest request) {
                    requests.put(request.id(), request);
                    return Mono.just(request);
                }

                @Override
                public Mono<InferenceRequest> findById(UUID requestId) {
                    return Mono.justOrEmpty(requests.get(requestId));
                }

                @Override
                public Mono<InferenceRequest> findByIdempotencyKey(String tenantId, String projectId, String idempotencyKey) {
                    return Flux.fromIterable(requests.values())
                            .filter(request -> request.tenantId().equals(tenantId)
                                    && request.projectId().equals(projectId)
                                    && request.idempotencyKey().equals(idempotencyKey))
                            .next();
                }

                @Override
                public Flux<InferenceRequest> findByConversationId(UUID conversationId) {
                    return Flux.fromIterable(requests.values().stream()
                            .filter(request -> request.conversationId().equals(conversationId))
                            .sorted((left, right) -> left.createdAt().compareTo(right.createdAt()))
                            .toList());
                }

                @Override
                public Mono<InferenceRequest> findLatestByConversationId(UUID conversationId) {
                    return Flux.fromIterable(requests.values().stream()
                                    .filter(request -> request.conversationId().equals(conversationId))
                                    .sorted((left, right) -> right.createdAt().compareTo(left.createdAt()))
                                    .toList())
                            .next();
                }

                @Override
                public Mono<InferenceRequest> findActiveByConversationId(UUID conversationId) {
                    return Flux.fromIterable(requests.values().stream()
                                    .filter(request -> request.conversationId().equals(conversationId))
                                    .filter(InferenceRequest::active)
                                    .sorted((left, right) -> right.createdAt().compareTo(left.createdAt()))
                                    .toList())
                            .next();
                }

                @Override
                public Mono<Void> markStreaming(UUID requestId, Instant firstTokenAt) {
                    replace(requestId, InferenceStatus.STREAMING, firstTokenAt, null, null, null, null);
                    return Mono.empty();
                }

                @Override
                public Mono<Void> markCompleted(UUID requestId, String outputContentHash, Instant completedAt) {
                    replace(requestId, InferenceStatus.COMPLETED, null, completedAt, null, null, outputContentHash);
                    return Mono.empty();
                }

                @Override
                public Mono<Void> markCancelled(UUID requestId, Instant cancelledAt) {
                    replace(requestId, InferenceStatus.CANCELLED, null, null, cancelledAt, null, null);
                    return Mono.empty();
                }

                @Override
                public Mono<Void> markFailed(UUID requestId, Instant failedAt) {
                    replace(requestId, InferenceStatus.FAILED, null, null, null, failedAt, null);
                    return Mono.empty();
                }

                @Override
                public Mono<Void> updateStatus(UUID requestId, InferenceStatus status, Instant updatedAt) {
                    replace(requestId, status, null, null, null, null, null);
                    return Mono.empty();
                }
            };
        }

        InferenceUsageRepository inferenceUsageRepository() {
            return new InferenceUsageRepository() {
                @Override
                public Mono<Void> save(InferenceUsage usage) {
                    usages.put(usage.inferenceRequestId(), usage);
                    return Mono.empty();
                }

                @Override
                public Mono<InferenceUsage> findByRequestId(UUID requestId) {
                    return Mono.justOrEmpty(usages.get(requestId));
                }
            };
        }

        InferenceErrorRepository inferenceErrorRepository() {
            return new InferenceErrorRepository() {
                @Override
                public Mono<Void> save(InferenceError error) {
                    errors.add(error);
                    return Mono.empty();
                }

                @Override
                public Mono<InferenceError> findLatest(UUID requestId) {
                    return Flux.fromIterable(errors)
                            .filter(error -> error.inferenceRequestId().equals(requestId))
                            .next();
                }
            };
        }

        InferenceCancellationRepository inferenceCancellationRepository() {
            return new InferenceCancellationRepository() {
                @Override
                public Mono<Void> save(InferenceCancellation cancellation) {
                    cancellations.add(cancellation);
                    return Mono.empty();
                }

                @Override
                public Mono<InferenceCancellation> findLatest(UUID requestId) {
                    return Flux.fromIterable(cancellations)
                            .filter(cancellation -> cancellation.inferenceRequestId().equals(requestId))
                            .next();
                }
            };
        }

        ModelCatalogRepository modelCatalogRepository() {
            return new ModelCatalogRepository() {
                @Override
                public Mono<ModelCatalogEntry> findEnabledModel(String providerKey, String modelKey) {
                    return Mono.just(new ModelCatalogEntry(
                            UUID.randomUUID(), UUID.randomUUID(), providerKey, "Provider", modelKey, "Model", 1_000_000, 8192, true, false));
                }

                @Override
                public Flux<ModelCatalogEntry> findAllEnabled() {
                    return Flux.empty();
                }
            };
        }

        ActiveStreamStateStore activeStreamStateStore() {
            return new ActiveStreamStateStore() {
                @Override
                public Mono<Void> register(UUID requestId, UUID conversationId, Duration ttl) {
                    return Mono.empty();
                }

                @Override
                public Mono<Void> appendEvent(UUID requestId, UUID conversationId, StreamEvent event, Duration ttl) {
                    return Mono.empty();
                }

                @Override
                public Flux<StreamEvent> replayEvents(UUID conversationId, String afterEventId) {
                    return Flux.empty();
                }

                @Override
                public Mono<UUID> findActiveRequestId(UUID conversationId) {
                    return Mono.empty();
                }

                @Override
                public Mono<Void> requestCancellation(UUID requestId, Duration ttl) {
                    cancelRequested.add(requestId);
                    return Mono.empty();
                }

                @Override
                public Mono<Boolean> cancellationRequested(UUID requestId) {
                    return Mono.just(cancelRequested.contains(requestId));
                }

                @Override
                public Mono<Void> clear(UUID requestId) {
                    return Mono.empty();
                }
            };
        }

        ProviderClientRegistry providerClientRegistry() {
            return providerKey -> Mono.just(providerClient());
        }

        ProviderClient providerClient() {
            return new ProviderClient() {
                @Override
                public String providerKey() {
                    return "gemini";
                }

                @Override
                public Flux<ProviderStreamChunk> stream(ProviderRequest request) {
                    providerRequests.add(request);
                    return Flux.just(new ProviderStreamChunk("done", 3, 5, "STOP", "test"));
                }

                @Override
                public Mono<ProviderCancellationResult> cancel(UUID requestId) {
                    return Mono.just(new ProviderCancellationResult(false, false));
                }
            };
        }

        LifecycleEventPublisher lifecycleEventPublisher() {
            return (eventName, tenantId, projectId, correlationId, traceparent, idempotencyKey, payload) -> Mono.empty();
        }

        private void replace(
                UUID requestId,
                InferenceStatus status,
                Instant firstTokenAt,
                Instant completedAt,
                Instant cancelledAt,
                Instant failedAt,
                String outputContentHash
        ) {
            InferenceRequest request = requests.get(requestId);
            Instant updatedAt = Instant.now();
            requests.put(requestId, new InferenceRequest(
                    request.id(),
                    request.tenantId(),
                    request.projectId(),
                    request.conversationId(),
                    request.providerId(),
                    request.modelId(),
                    request.providerKey(),
                    request.modelKey(),
                    request.idempotencyKey(),
                    status,
                    request.streaming(),
                    request.requestMetadata(),
                    request.inputMessageCount(),
                    request.inputContentHash(),
                    outputContentHash == null ? request.outputContentHash() : outputContentHash,
                    request.redisStreamKey(),
                    request.createdAt(),
                    request.startedAt(),
                    firstTokenAt == null ? request.firstTokenAt() : firstTokenAt,
                    completedAt == null ? request.completedAt() : completedAt,
                    cancelledAt == null ? request.cancelledAt() : cancelledAt,
                    failedAt == null ? request.failedAt() : failedAt,
                    updatedAt));
        }
    }
}
