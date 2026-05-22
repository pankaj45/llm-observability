package com.llmobservability.platform.inferencegateway.adapter.out.postgres;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.llmobservability.platform.inferencegateway.application.port.out.InferenceRequestRepository;
import com.llmobservability.platform.inferencegateway.domain.model.InferenceRequest;
import com.llmobservability.platform.inferencegateway.domain.model.InferenceStatus;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

import static com.llmobservability.platform.inferencegateway.adapter.out.postgres.PostgresBindingSupport.bindNullable;

@Repository
class PostgresInferenceRequestRepository implements InferenceRequestRepository {
    private final DatabaseClient databaseClient;
    private final ObjectMapper objectMapper;

    PostgresInferenceRequestRepository(DatabaseClient databaseClient, ObjectMapper objectMapper) {
        this.databaseClient = databaseClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<InferenceRequest> save(InferenceRequest request) {
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql("""
                        INSERT INTO inference_request (
                            id, tenant_id, project_id, conversation_id, provider_id, model_id, provider_key, model_key,
                            idempotency_key, status, streaming, request_metadata, input_message_count,
                            input_content_hash, output_content_hash, redis_stream_key, created_at, started_at,
                            first_token_at, completed_at, cancelled_at, failed_at, updated_at
                        ) VALUES (
                            :id, :tenantId, :projectId, :conversationId, :providerId, :modelId, :providerKey, :modelKey,
                            :idempotencyKey, :status, :streaming, CAST(:requestMetadata AS jsonb), :inputMessageCount,
                            :inputContentHash, :outputContentHash, :redisStreamKey, :createdAt, :startedAt,
                            :firstTokenAt, :completedAt, :cancelledAt, :failedAt, :updatedAt
                        )
                        """)
                .bind("id", request.id())
                .bind("tenantId", request.tenantId())
                .bind("projectId", request.projectId())
                .bind("conversationId", request.conversationId())
                .bind("providerId", request.providerId())
                .bind("modelId", request.modelId())
                .bind("providerKey", request.providerKey())
                .bind("modelKey", request.modelKey())
                .bind("idempotencyKey", request.idempotencyKey())
                .bind("status", request.status().name())
                .bind("streaming", request.streaming())
                .bind("requestMetadata", PostgresBindingSupport.json(objectMapper, request.requestMetadata()))
                .bind("inputMessageCount", request.inputMessageCount())
                .bind("inputContentHash", request.inputContentHash())
                .bind("redisStreamKey", request.redisStreamKey())
                .bind("createdAt", request.createdAt())
                .bind("startedAt", request.startedAt())
                .bind("updatedAt", request.updatedAt());
        spec = bindNullable(spec, "outputContentHash", request.outputContentHash(), String.class);
        spec = bindNullable(spec, "firstTokenAt", request.firstTokenAt(), Instant.class);
        spec = bindNullable(spec, "completedAt", request.completedAt(), Instant.class);
        spec = bindNullable(spec, "cancelledAt", request.cancelledAt(), Instant.class);
        spec = bindNullable(spec, "failedAt", request.failedAt(), Instant.class);
        return spec.fetch().rowsUpdated().thenReturn(request);
    }

    @Override
    public Mono<InferenceRequest> findById(UUID requestId) {
        return one(select("WHERE id = :id").bind("id", requestId));
    }

    @Override
    public Mono<InferenceRequest> findByIdempotencyKey(String tenantId, String projectId, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Mono.empty();
        }
        return one(select("WHERE tenant_id = :tenantId AND project_id = :projectId AND idempotency_key = :idempotencyKey")
                .bind("tenantId", tenantId)
                .bind("projectId", projectId)
                .bind("idempotencyKey", idempotencyKey));
    }

    @Override
    public Flux<InferenceRequest> findByConversationId(UUID conversationId) {
        return select("WHERE conversation_id = :conversationId ORDER BY created_at ASC, id ASC")
                .bind("conversationId", conversationId)
                .map((row, metadata) -> map(row))
                .all();
    }

    @Override
    public Mono<InferenceRequest> findLatestByConversationId(UUID conversationId) {
        return one(select("WHERE conversation_id = :conversationId ORDER BY created_at DESC, id ASC LIMIT 1")
                .bind("conversationId", conversationId));
    }

    @Override
    public Mono<InferenceRequest> findActiveByConversationId(UUID conversationId) {
        return one(select("""
                        WHERE conversation_id = :conversationId
                          AND status IN ('ACCEPTED', 'STREAMING')
                        ORDER BY created_at DESC, id ASC
                        LIMIT 1
                        """)
                .bind("conversationId", conversationId));
    }

    @Override
    public Mono<Void> markStreaming(UUID requestId, Instant firstTokenAt) {
        return databaseClient.sql("""
                        UPDATE inference_request
                        SET status = :status, first_token_at = COALESCE(first_token_at, :firstTokenAt), updated_at = :updatedAt
                        WHERE id = :id
                        """)
                .bind("id", requestId)
                .bind("status", InferenceStatus.STREAMING.name())
                .bind("firstTokenAt", firstTokenAt)
                .bind("updatedAt", firstTokenAt)
                .fetch()
                .rowsUpdated()
                .then();
    }

    @Override
    public Mono<Void> markCompleted(UUID requestId, String outputContentHash, Instant completedAt) {
        return databaseClient.sql("""
                        UPDATE inference_request
                        SET status = :status, output_content_hash = :outputContentHash, completed_at = :completedAt, updated_at = :completedAt
                        WHERE id = :id
                        """)
                .bind("id", requestId)
                .bind("status", InferenceStatus.COMPLETED.name())
                .bind("outputContentHash", outputContentHash)
                .bind("completedAt", completedAt)
                .fetch()
                .rowsUpdated()
                .then();
    }

    @Override
    public Mono<Void> markCancelled(UUID requestId, Instant cancelledAt) {
        return updateStatusTimestamp(requestId, InferenceStatus.CANCELLED, "cancelled_at", cancelledAt);
    }

    @Override
    public Mono<Void> markFailed(UUID requestId, Instant failedAt) {
        return updateStatusTimestamp(requestId, InferenceStatus.FAILED, "failed_at", failedAt);
    }

    @Override
    public Mono<Void> updateStatus(UUID requestId, InferenceStatus status, Instant updatedAt) {
        return databaseClient.sql("""
                        UPDATE inference_request
                        SET status = :status, updated_at = :updatedAt
                        WHERE id = :id
                        """)
                .bind("id", requestId)
                .bind("status", status.name())
                .bind("updatedAt", updatedAt)
                .fetch()
                .rowsUpdated()
                .then();
    }

    private Mono<Void> updateStatusTimestamp(UUID requestId, InferenceStatus status, String timestampColumn, Instant timestamp) {
        return databaseClient.sql("""
                        UPDATE inference_request
                        SET status = :status, %s = :timestamp, updated_at = :timestamp
                        WHERE id = :id
                        """.formatted(timestampColumn))
                .bind("id", requestId)
                .bind("status", status.name())
                .bind("timestamp", timestamp)
                .fetch()
                .rowsUpdated()
                .then();
    }

    private DatabaseClient.GenericExecuteSpec select(String predicate) {
        return databaseClient.sql("""
                        SELECT id, tenant_id, project_id, conversation_id, provider_id, model_id, provider_key, model_key,
                               idempotency_key, status, streaming, request_metadata::text AS request_metadata,
                               input_message_count, input_content_hash, output_content_hash, redis_stream_key,
                               created_at, started_at, first_token_at, completed_at, cancelled_at, failed_at, updated_at
                        FROM inference_request
                        %s
                        """.formatted(predicate));
    }

    private InferenceRequest map(io.r2dbc.spi.Row row) {
        return new InferenceRequest(
                PostgresBindingSupport.uuid(row, "id"),
                PostgresBindingSupport.string(row, "tenant_id"),
                PostgresBindingSupport.string(row, "project_id"),
                PostgresBindingSupport.uuid(row, "conversation_id"),
                PostgresBindingSupport.uuid(row, "provider_id"),
                PostgresBindingSupport.uuid(row, "model_id"),
                PostgresBindingSupport.string(row, "provider_key"),
                PostgresBindingSupport.string(row, "model_key"),
                PostgresBindingSupport.string(row, "idempotency_key"),
                InferenceStatus.valueOf(PostgresBindingSupport.string(row, "status")),
                PostgresBindingSupport.bool(row, "streaming"),
                PostgresBindingSupport.stringMap(objectMapper, PostgresBindingSupport.string(row, "request_metadata")),
                PostgresBindingSupport.integer(row, "input_message_count"),
                PostgresBindingSupport.string(row, "input_content_hash"),
                PostgresBindingSupport.string(row, "output_content_hash"),
                PostgresBindingSupport.string(row, "redis_stream_key"),
                PostgresBindingSupport.instant(row, "created_at"),
                PostgresBindingSupport.instant(row, "started_at"),
                PostgresBindingSupport.instant(row, "first_token_at"),
                PostgresBindingSupport.instant(row, "completed_at"),
                PostgresBindingSupport.instant(row, "cancelled_at"),
                PostgresBindingSupport.instant(row, "failed_at"),
                PostgresBindingSupport.instant(row, "updated_at"));
    }

    private Mono<InferenceRequest> one(DatabaseClient.GenericExecuteSpec spec) {
        return spec.map((row, metadata) -> map(row)).one();
    }
}
