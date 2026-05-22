package com.llmobservability.platform.inferencegateway.adapter.out.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.llmobservability.platform.inferencegateway.application.port.out.ActiveStreamStateStore;
import com.llmobservability.platform.inferencegateway.domain.model.StreamEvent;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
class RedisActiveStreamStateStore implements ActiveStreamStateStore {
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    RedisActiveStreamStateStore(ReactiveStringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> register(UUID requestId, UUID conversationId, Duration ttl) {
        return redisTemplate.opsForValue()
                .set(streamKey(requestId), conversationId.toString(), ttl)
                .then(redisTemplate.opsForValue().set(activeConversationKey(conversationId), requestId.toString(), ttl))
                .then();
    }

    @Override
    public Mono<Void> appendEvent(UUID requestId, UUID conversationId, StreamEvent event, Duration ttl) {
        String eventJson = encode(event);
        return redisTemplate.opsForList()
                .rightPush(conversationEventsKey(conversationId), eventJson)
                .then(redisTemplate.expire(conversationEventsKey(conversationId), ttl))
                .then(redisTemplate.opsForList().rightPush(requestEventsKey(requestId), eventJson))
                .then(redisTemplate.expire(requestEventsKey(requestId), ttl))
                .then();
    }

    @Override
    public Flux<StreamEvent> replayEvents(UUID conversationId, String afterEventId) {
        AtomicBoolean emit = new AtomicBoolean(afterEventId == null || afterEventId.isBlank());
        return redisTemplate.opsForList()
                .range(conversationEventsKey(conversationId), 0, -1)
                .map(this::decode)
                .filter(event -> {
                    if (emit.get()) {
                        return true;
                    }
                    if (event.id().equals(afterEventId)) {
                        emit.set(true);
                    }
                    return false;
                });
    }

    @Override
    public Mono<UUID> findActiveRequestId(UUID conversationId) {
        return redisTemplate.opsForValue()
                .get(activeConversationKey(conversationId))
                .map(UUID::fromString);
    }

    @Override
    public Mono<Void> requestCancellation(UUID requestId, Duration ttl) {
        return redisTemplate.opsForValue()
                .set(cancelKey(requestId), "true", ttl)
                .then();
    }

    @Override
    public Mono<Boolean> cancellationRequested(UUID requestId) {
        return redisTemplate.hasKey(cancelKey(requestId));
    }

    @Override
    public Mono<Void> clear(UUID requestId) {
        return redisTemplate.opsForValue()
                .get(streamKey(requestId))
                .flatMap(conversationId -> redisTemplate.delete(streamKey(requestId), cancelKey(requestId), activeConversationKey(UUID.fromString(conversationId))).then())
                .switchIfEmpty(redisTemplate.delete(streamKey(requestId), cancelKey(requestId)).then());
    }

    private String streamKey(UUID requestId) {
        return "inference:stream:" + requestId;
    }

    private String cancelKey(UUID requestId) {
        return "inference:cancel:" + requestId;
    }

    private String activeConversationKey(UUID conversationId) {
        return "conversation:" + conversationId + ":active-request";
    }

    private String conversationEventsKey(UUID conversationId) {
        return "conversation:" + conversationId + ":stream-events";
    }

    private String requestEventsKey(UUID requestId) {
        return "inference:" + requestId + ":stream-events";
    }

    private String encode(StreamEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to encode stream event", exception);
        }
    }

    private StreamEvent decode(String value) {
        try {
            return objectMapper.readValue(value, StreamEvent.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to decode stream event", exception);
        }
    }
}
