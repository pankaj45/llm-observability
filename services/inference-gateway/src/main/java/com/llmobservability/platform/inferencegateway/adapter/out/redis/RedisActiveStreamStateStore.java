package com.llmobservability.platform.inferencegateway.adapter.out.redis;

import com.llmobservability.platform.inferencegateway.application.port.out.ActiveStreamStateStore;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;

@Component
class RedisActiveStreamStateStore implements ActiveStreamStateStore {
    private final ReactiveStringRedisTemplate redisTemplate;

    RedisActiveStreamStateStore(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Void> register(UUID requestId, UUID conversationId, Duration ttl) {
        return redisTemplate.opsForValue()
                .set(streamKey(requestId), conversationId.toString(), ttl)
                .then();
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
        return redisTemplate.delete(streamKey(requestId), cancelKey(requestId)).then();
    }

    private String streamKey(UUID requestId) {
        return "inference:stream:" + requestId;
    }

    private String cancelKey(UUID requestId) {
        return "inference:cancel:" + requestId;
    }
}
