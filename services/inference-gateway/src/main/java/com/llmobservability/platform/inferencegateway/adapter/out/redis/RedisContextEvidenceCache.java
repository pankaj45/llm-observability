package com.llmobservability.platform.inferencegateway.adapter.out.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.llmobservability.platform.inferencegateway.application.port.out.ContextEvidenceCache;
import com.llmobservability.platform.inferencegateway.application.service.context.ContextEvidence;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

@Component
class RedisContextEvidenceCache implements ContextEvidenceCache {
    private static final TypeReference<List<ContextEvidence>> EVIDENCE_LIST = new TypeReference<>() {
    };

    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    RedisContextEvidenceCache(ReactiveStringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<List<ContextEvidence>> get(String key) {
        return redisTemplate.opsForValue()
                .get(key)
                .map(this::decode)
                .defaultIfEmpty(List.of())
                .onErrorReturn(List.of());
    }

    @Override
    public Mono<Void> put(String key, List<ContextEvidence> evidence, Duration ttl) {
        if (evidence == null || evidence.isEmpty()) {
            return Mono.empty();
        }
        return redisTemplate.opsForValue()
                .set(key, encode(evidence), ttl)
                .then()
                .onErrorResume(error -> Mono.empty());
    }

    private String encode(List<ContextEvidence> evidence) {
        try {
            return objectMapper.writeValueAsString(evidence);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to encode context evidence", e);
        }
    }

    private List<ContextEvidence> decode(String value) {
        try {
            return objectMapper.readValue(value, EVIDENCE_LIST);
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }
}
