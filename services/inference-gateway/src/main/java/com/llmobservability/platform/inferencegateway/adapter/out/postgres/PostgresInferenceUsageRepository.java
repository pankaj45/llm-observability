package com.llmobservability.platform.inferencegateway.adapter.out.postgres;

import com.llmobservability.platform.inferencegateway.application.port.out.InferenceUsageRepository;
import com.llmobservability.platform.inferencegateway.domain.model.InferenceUsage;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
class PostgresInferenceUsageRepository implements InferenceUsageRepository {
    private final DatabaseClient databaseClient;

    PostgresInferenceUsageRepository(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<Void> save(InferenceUsage usage) {
        return databaseClient.sql("""
                        INSERT INTO inference_usage (
                            id, inference_request_id, input_tokens, output_tokens, total_tokens,
                            provider_reported_units, estimated_cost_amount, estimated_cost_currency, created_at
                        ) VALUES (
                            :id, :requestId, :inputTokens, :outputTokens, :totalTokens,
                            :providerReportedUnits, :estimatedCostAmount, :estimatedCostCurrency, :createdAt
                        )
                        """)
                .bind("id", usage.id())
                .bind("requestId", usage.inferenceRequestId())
                .bind("inputTokens", usage.inputTokens())
                .bind("outputTokens", usage.outputTokens())
                .bind("totalTokens", usage.totalTokens())
                .bind("providerReportedUnits", usage.providerReportedUnits())
                .bind("estimatedCostAmount", usage.estimatedCostAmount())
                .bind("estimatedCostCurrency", usage.estimatedCostCurrency())
                .bind("createdAt", usage.createdAt())
                .fetch()
                .rowsUpdated()
                .then();
    }

    @Override
    public Mono<InferenceUsage> findByRequestId(UUID requestId) {
        return databaseClient.sql("""
                        SELECT id, inference_request_id, input_tokens, output_tokens, total_tokens,
                               provider_reported_units, estimated_cost_amount, estimated_cost_currency, created_at
                        FROM inference_usage
                        WHERE inference_request_id = :requestId
                        ORDER BY created_at DESC
                        LIMIT 1
                        """)
                .bind("requestId", requestId)
                .map((row, metadata) -> new InferenceUsage(
                        PostgresBindingSupport.uuid(row, "id"),
                        PostgresBindingSupport.uuid(row, "inference_request_id"),
                        PostgresBindingSupport.integer(row, "input_tokens"),
                        PostgresBindingSupport.integer(row, "output_tokens"),
                        PostgresBindingSupport.integer(row, "total_tokens"),
                        PostgresBindingSupport.string(row, "provider_reported_units"),
                        PostgresBindingSupport.decimal(row, "estimated_cost_amount"),
                        PostgresBindingSupport.string(row, "estimated_cost_currency"),
                        PostgresBindingSupport.instant(row, "created_at")))
                .one();
    }
}
