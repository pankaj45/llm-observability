package com.llmobservability.platform.inferencegateway.adapter.out.postgres;

import com.llmobservability.platform.inferencegateway.application.port.out.InferenceCancellationRepository;
import com.llmobservability.platform.inferencegateway.domain.model.InferenceCancellation;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
class PostgresInferenceCancellationRepository implements InferenceCancellationRepository {
    private final DatabaseClient databaseClient;

    PostgresInferenceCancellationRepository(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<Void> save(InferenceCancellation cancellation) {
        return databaseClient.sql("""
                        INSERT INTO inference_cancellation (
                            id, inference_request_id, requested_by, reason,
                            provider_cancellation_attempted, provider_cancellation_succeeded,
                            created_at, resolved_at
                        ) VALUES (
                            :id, :requestId, :requestedBy, :reason,
                            :providerCancellationAttempted, :providerCancellationSucceeded,
                            :createdAt, :resolvedAt
                        )
                        """)
                .bind("id", cancellation.id())
                .bind("requestId", cancellation.inferenceRequestId())
                .bind("requestedBy", cancellation.requestedBy())
                .bind("reason", cancellation.reason())
                .bind("providerCancellationAttempted", cancellation.providerCancellationAttempted())
                .bind("providerCancellationSucceeded", cancellation.providerCancellationSucceeded())
                .bind("createdAt", cancellation.createdAt())
                .bind("resolvedAt", cancellation.resolvedAt())
                .fetch()
                .rowsUpdated()
                .then();
    }

    @Override
    public Mono<InferenceCancellation> findLatest(UUID requestId) {
        return databaseClient.sql("""
                        SELECT id, inference_request_id, requested_by, reason,
                               provider_cancellation_attempted, provider_cancellation_succeeded,
                               created_at, resolved_at
                        FROM inference_cancellation
                        WHERE inference_request_id = :requestId
                        ORDER BY created_at DESC
                        LIMIT 1
                        """)
                .bind("requestId", requestId)
                .map((row, metadata) -> new InferenceCancellation(
                        PostgresBindingSupport.uuid(row, "id"),
                        PostgresBindingSupport.uuid(row, "inference_request_id"),
                        PostgresBindingSupport.string(row, "requested_by"),
                        PostgresBindingSupport.string(row, "reason"),
                        PostgresBindingSupport.bool(row, "provider_cancellation_attempted"),
                        PostgresBindingSupport.bool(row, "provider_cancellation_succeeded"),
                        PostgresBindingSupport.instant(row, "created_at"),
                        PostgresBindingSupport.instant(row, "resolved_at")))
                .one();
    }
}
