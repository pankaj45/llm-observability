package com.llmobservability.platform.inferencegateway.adapter.out.postgres;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.llmobservability.platform.inferencegateway.application.port.out.ContextToolInvocationRepository;
import com.llmobservability.platform.inferencegateway.application.service.context.ContextToolInvocation;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
class PostgresContextToolInvocationRepository implements ContextToolInvocationRepository {
    private final DatabaseClient databaseClient;
    private final ObjectMapper objectMapper;

    PostgresContextToolInvocationRepository(DatabaseClient databaseClient, ObjectMapper objectMapper) {
        this.databaseClient = databaseClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> save(ContextToolInvocation invocation) {
        return databaseClient.sql("""
                        INSERT INTO context_tool_invocation (
                          id, tenant_id, project_id, conversation_id, inference_request_id,
                          tool_name, tool_provider, input_hash, status, started_at, completed_at,
                          latency_ms, cache_hit, result_count, source_urls, error_code, metadata
                        ) VALUES (
                          :id, :tenantId, :projectId, :conversationId, :inferenceRequestId,
                          :toolName, :toolProvider, :inputHash, :status, :startedAt, :completedAt,
                          :latencyMs, :cacheHit, :resultCount, (:sourceUrls)::jsonb, :errorCode, (:metadata)::jsonb
                        )
                        """)
                .bind("id", invocation.id())
                .bind("tenantId", invocation.tenantId())
                .bind("projectId", invocation.projectId())
                .bind("conversationId", invocation.conversationId())
                .bind("inferenceRequestId", invocation.inferenceRequestId())
                .bind("toolName", invocation.toolName())
                .bind("toolProvider", invocation.toolProvider())
                .bind("inputHash", invocation.inputHash())
                .bind("status", invocation.status().name())
                .bind("startedAt", invocation.startedAt())
                .bind("completedAt", invocation.completedAt())
                .bind("latencyMs", invocation.latencyMs())
                .bind("cacheHit", invocation.cacheHit())
                .bind("resultCount", invocation.resultCount())
                .bind("sourceUrls", json(invocation.sourceUrls()))
                .bind("metadata", json(invocation.metadata()))
                .filter(statement -> {
                    if (invocation.errorCode() == null) {
                        statement.bindNull("errorCode", String.class);
                    } else {
                        statement.bind("errorCode", invocation.errorCode());
                    }
                    return statement;
                })
                .fetch()
                .rowsUpdated()
                .then();
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Unable to serialize context tool invocation JSON", e);
        }
    }
}
