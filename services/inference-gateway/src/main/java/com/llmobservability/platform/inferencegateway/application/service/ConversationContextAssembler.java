package com.llmobservability.platform.inferencegateway.application.service;

import com.llmobservability.platform.inferencegateway.application.port.in.StartInferenceCommand;
import com.llmobservability.platform.inferencegateway.application.port.out.ConversationCompactionPort;
import com.llmobservability.platform.inferencegateway.application.port.out.ConversationContextSnapshotRepository;
import com.llmobservability.platform.inferencegateway.config.ContextCompactionProperties;
import com.llmobservability.platform.inferencegateway.domain.model.ConversationContextSnapshot;
import com.llmobservability.platform.inferencegateway.domain.model.InferenceRequest;
import com.llmobservability.platform.inferencegateway.domain.model.MessageRole;
import com.llmobservability.platform.inferencegateway.domain.model.ModelCatalogEntry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class ConversationContextAssembler {
    private static final Logger log = LoggerFactory.getLogger(ConversationContextAssembler.class);
    private static final String STRATEGY = "ROLLING_SUMMARY_V1";

    private final ConversationContextSnapshotRepository snapshotRepository;
    private final ConversationCompactionPort compactionPort;
    private final ContextCompactionProperties properties;
    private final MeterRegistry meterRegistry;

    public ConversationContextAssembler(
            ConversationContextSnapshotRepository snapshotRepository,
            ConversationCompactionPort compactionPort,
            ContextCompactionProperties properties,
            MeterRegistry meterRegistry
    ) {
        this.snapshotRepository = snapshotRepository;
        this.compactionPort = compactionPort;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    public Mono<ConversationContextAssembly> assemble(
            InferenceRequest request,
            ModelCatalogEntry model,
            List<StartInferenceCommand.Message> providerMessages,
            Map<String, Object> parameters
    ) {
        int exactTokens = estimate(providerMessages);
        if (!properties.isEnabled() || providerMessages.size() <= properties.minExactRecentMessagesValue()) {
            return Mono.just(ConversationContextAssembly.exact(providerMessages, exactTokens));
        }

        int usableInputBudget = usableInputBudget(model, parameters);
        int triggerTokens = Math.max(1, (int) Math.floor(usableInputBudget * properties.triggerThresholdRatioValue()));
        if (exactTokens <= triggerTokens) {
            return Mono.just(ConversationContextAssembly.exact(providerMessages, exactTokens));
        }

        int compactEndSequence = providerMessages.size() - properties.minExactRecentMessagesValue() - 1;
        if (compactEndSequence < 0) {
            return Mono.just(ConversationContextAssembly.exact(providerMessages, exactTokens));
        }

        return snapshotRepository.findLatest(request.conversationId(), request.providerKey(), request.modelKey())
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .flatMap(latestSnapshot -> {
                    Optional<ConversationContextSnapshot> reusable = latestSnapshot
                            .filter(snapshot -> canReuse(snapshot, providerMessages, compactEndSequence, usableInputBudget));
                    if (reusable.isPresent()) {
                        ConversationContextAssembly assembly = fromSnapshot(providerMessages, reusable.get(), exactTokens);
                        recordCompaction("reused");
                        return Mono.just(assembly);
                    }
                    return createSnapshot(request, providerMessages, compactEndSequence, latestSnapshot, exactTokens)
                            .onErrorResume(error -> {
                                log.warn("context_compaction.failed requestId={} conversationId={} errorType={}",
                                        request.id(), request.conversationId(), error.getClass().getSimpleName());
                                recordCompaction("failed");
                                return Mono.just(fallback(providerMessages, exactTokens));
                            });
                });
    }

    private Mono<ConversationContextAssembly> createSnapshot(
            InferenceRequest request,
            List<StartInferenceCommand.Message> providerMessages,
            int compactEndSequence,
            Optional<ConversationContextSnapshot> priorSnapshot,
            int exactTokens
    ) {
        Optional<ConversationContextSnapshot> usablePriorSnapshot = priorSnapshot
                .filter(snapshot -> STRATEGY.equals(snapshot.compactionStrategy()));
        int compactStartSequence = usablePriorSnapshot
                .map(snapshot -> Math.min(snapshot.sourceEndSequence() + 1, compactEndSequence + 1))
                .orElse(0);
        List<StartInferenceCommand.Message> messagesToCompact = providerMessages.subList(compactStartSequence, compactEndSequence + 1);
        ConversationCompactionPort.CompactionRequest compactionRequest = new ConversationCompactionPort.CompactionRequest(
                request.conversationId(),
                request.id(),
                request.providerKey(),
                request.modelKey(),
                compactStartSequence,
                compactEndSequence,
                usablePriorSnapshot,
                List.copyOf(messagesToCompact),
                properties.maxSummaryTokensValue());

        return compactionPort.compact(compactionRequest)
                .flatMap(result -> {
                    Instant now = Instant.now();
                    ConversationContextSnapshot snapshot = new ConversationContextSnapshot(
                            UUID.randomUUID(),
                            request.conversationId(),
                            0,
                            compactEndSequence,
                            result.summaryContent(),
                            ContentHasher.sha256(result.summaryContent()),
                            result.estimatedTokens(),
                            STRATEGY,
                            request.providerKey(),
                            request.modelKey(),
                            request.id(),
                            Map.of(
                                    "sourceMessageCount", Integer.toString(messagesToCompact.size()),
                                    "inputTokensBefore", Integer.toString(exactTokens)),
                            now);
                    return snapshotRepository.save(snapshot)
                            .onErrorResume(error -> {
                                log.warn("context_snapshot.save.failed requestId={} conversationId={} errorType={}",
                                        request.id(), request.conversationId(), error.getClass().getSimpleName());
                                return Mono.empty();
                            })
                            .thenReturn(fromSnapshot(providerMessages, snapshot, exactTokens));
                })
                .doOnSuccess(ignored -> recordCompaction("created"));
    }

    private boolean canReuse(
            ConversationContextSnapshot snapshot,
            List<StartInferenceCommand.Message> providerMessages,
            int compactEndSequence,
            int usableInputBudget
    ) {
        if (!STRATEGY.equals(snapshot.compactionStrategy())) {
            return false;
        }
        if (snapshot.sourceEndSequence() >= compactEndSequence) {
            return true;
        }

        int newAgedMessages = compactEndSequence - snapshot.sourceEndSequence();
        int newAgedTokens = estimate(providerMessages.subList(snapshot.sourceEndSequence() + 1, compactEndSequence + 1));
        int targetTokens = Math.max(1, (int) Math.floor(usableInputBudget * properties.targetThresholdRatioValue()));
        int assembledTokens = estimate(fromSnapshot(providerMessages, snapshot, estimate(providerMessages)).providerMessages());

        return newAgedMessages < properties.minMessagesBeyondSnapshotValue()
                && newAgedTokens < properties.minTokensBeyondSnapshotValue()
                && assembledTokens <= targetTokens;
    }

    private ConversationContextAssembly fromSnapshot(
            List<StartInferenceCommand.Message> providerMessages,
            ConversationContextSnapshot snapshot,
            int exactTokens
    ) {
        List<StartInferenceCommand.Message> assembled = new ArrayList<>();
        assembled.add(new StartInferenceCommand.Message(MessageRole.SYSTEM, snapshotInstruction(snapshot)));
        int recentStart = Math.min(providerMessages.size(), snapshot.sourceEndSequence() + 1);
        assembled.addAll(providerMessages.subList(recentStart, providerMessages.size()));
        return new ConversationContextAssembly(
                List.copyOf(assembled),
                true,
                Optional.of(snapshot.id()),
                exactTokens,
                estimate(assembled));
    }

    private ConversationContextAssembly fallback(List<StartInferenceCommand.Message> providerMessages, int exactTokens) {
        int recentStart = Math.max(0, providerMessages.size() - properties.minExactRecentMessagesValue());
        List<StartInferenceCommand.Message> recentMessages = providerMessages.subList(recentStart, providerMessages.size());
        return new ConversationContextAssembly(List.copyOf(recentMessages), true, Optional.empty(), exactTokens, estimate(recentMessages));
    }

    private String snapshotInstruction(ConversationContextSnapshot snapshot) {
        return "Earlier conversation summary (messages "
                + snapshot.sourceStartSequence()
                + "-"
                + snapshot.sourceEndSequence()
                + "). Use this as continuity context; exact recent messages follow.\n"
                + snapshot.summaryContent();
    }

    private int usableInputBudget(ModelCatalogEntry model, Map<String, Object> parameters) {
        int contextWindow = model.contextWindowTokens();
        int outputReserve = outputReserve(contextWindow, model.maxOutputTokens(), parameters);
        int reserve = outputReserve
                + properties.runtimeContextReserveTokensValue()
                + properties.toolEvidenceReserveTokensValue();
        return Math.max(1, contextWindow - reserve);
    }

    private int outputReserve(int contextWindow, int modelMaxOutputTokens, Map<String, Object> parameters) {
        Object requested = parameters == null ? null : firstPresent(parameters, "maxOutputTokens", "maxTokens");
        int reserve = parsePositiveInt(requested).orElse(Math.min(modelMaxOutputTokens, Math.max(256, contextWindow / 4)));
        return Math.min(reserve, Math.max(1, contextWindow / 2));
    }

    private Object firstPresent(Map<String, Object> parameters, String first, String second) {
        if (parameters.containsKey(first)) {
            return parameters.get(first);
        }
        return parameters.get(second);
    }

    private Optional<Integer> parsePositiveInt(Object value) {
        if (value instanceof Number number && number.intValue() > 0) {
            return Optional.of(number.intValue());
        }
        if (value instanceof String stringValue) {
            try {
                int parsed = Integer.parseInt(stringValue);
                return parsed > 0 ? Optional.of(parsed) : Optional.empty();
            } catch (NumberFormatException ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private int estimate(List<StartInferenceCommand.Message> messages) {
        return messages.stream()
                .mapToInt(message -> TokenEstimator.estimate(message.content()))
                .sum();
    }

    private void recordCompaction(String outcome) {
        Counter.builder("conversation_context_compactions_total")
                .description("Conversation context compaction decisions by outcome")
                .tag("outcome", outcome)
                .register(meterRegistry)
                .increment();
    }
}
