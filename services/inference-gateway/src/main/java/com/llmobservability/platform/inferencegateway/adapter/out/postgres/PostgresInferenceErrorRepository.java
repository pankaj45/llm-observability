package com.llmobservability.platform.inferencegateway.adapter.out.postgres;

import com.llmobservability.platform.inferencegateway.application.port.out.InferenceErrorRepository;
import com.llmobservability.platform.inferencegateway.domain.model.FailureStage;
import com.llmobservability.platform.inferencegateway.domain.model.InferenceError;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static com.llmobservability.platform.inferencegateway.adapter.out.postgres.PostgresBindingSupport.bindNullable;

@Repository
class PostgresInferenceErrorRepository implements InferenceErrorRepository {
    private final DatabaseClient databaseClient;

    PostgresInferenceErrorRepository(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<Void> save(InferenceError error) {
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql("""
                        INSERT INTO inference_error (
                            id, inference_request_id, failure_stage, error_code, provider_error_code, message,
                            retryable, created_at
                        ) VALUES (
                            :id, :requestId, :failureStage, :errorCode, :providerErrorCode, :message,
                            :retryable, :createdAt
                        )
                        """)
                .bind("id", error.id())
                .bind("requestId", error.inferenceRequestId())
                .bind("failureStage", error.failureStage().name())
                .bind("errorCode", error.errorCode())
                .bind("message", error.message())
                .bind("retryable", error.retryable())
                .bind("createdAt", error.createdAt());
        return bindNullable(spec, "providerErrorCode", error.providerErrorCode(), String.class)
                .fetch()
                .rowsUpdated()
                .then();
    }

    @Override
    public Mono<InferenceError> findLatest(UUID requestId) {
        return databaseClient.sql("""
                        SELECT id, inference_request_id, failure_stage, error_code, provider_error_code, message,
                               retryable, created_at
                        FROM inference_error
                        WHERE inference_request_id = :requestId
                        ORDER BY created_at DESC
                        LIMIT 1
                        """)
                .bind("requestId", requestId)
                .map((row, metadata) -> new InferenceError(
                        PostgresBindingSupport.uuid(row, "id"),
                        PostgresBindingSupport.uuid(row, "inference_request_id"),
                        FailureStage.valueOf(PostgresBindingSupport.string(row, "failure_stage")),
                        PostgresBindingSupport.string(row, "error_code"),
                        PostgresBindingSupport.string(row, "provider_error_code"),
                        PostgresBindingSupport.string(row, "message"),
                        PostgresBindingSupport.bool(row, "retryable"),
                        PostgresBindingSupport.instant(row, "created_at")))
                .one();
    }
}
