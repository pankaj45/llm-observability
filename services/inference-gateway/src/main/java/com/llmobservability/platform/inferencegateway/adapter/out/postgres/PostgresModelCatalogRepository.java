package com.llmobservability.platform.inferencegateway.adapter.out.postgres;

import com.llmobservability.platform.inferencegateway.application.port.out.ModelCatalogRepository;
import com.llmobservability.platform.inferencegateway.domain.model.ModelCatalogEntry;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
class PostgresModelCatalogRepository implements ModelCatalogRepository {

    private static final String BASE_SELECT = """
            SELECT provider.id              AS provider_id,
                   model.id                AS model_id,
                   provider.provider_key,
                   provider.display_name   AS provider_display_name,
                   model.model_key,
                   model.display_name      AS model_display_name,
                   model.context_window_tokens,
                   model.max_output_tokens,
                   provider.supports_streaming    AS provider_supports_streaming,
                   provider.supports_cancellation AS provider_supports_cancellation
            FROM llm_provider provider
            JOIN llm_model model ON model.provider_id = provider.id
            WHERE provider.enabled = true
              AND model.enabled = true
            """;

    private final DatabaseClient databaseClient;

    PostgresModelCatalogRepository(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<ModelCatalogEntry> findEnabledModel(String providerKey, String modelKey) {
        return databaseClient.sql(BASE_SELECT + """
                          AND provider.provider_key = :providerKey
                          AND model.model_key       = :modelKey
                        """)
                .bind("providerKey", providerKey)
                .bind("modelKey", modelKey)
                .map(this::toEntry)
                .one();
    }

    @Override
    public Flux<ModelCatalogEntry> findAllEnabled() {
        return databaseClient.sql(BASE_SELECT + "ORDER BY provider.provider_key, model.model_key")
                .map(this::toEntry)
                .all();
    }

    private ModelCatalogEntry toEntry(io.r2dbc.spi.Row row, io.r2dbc.spi.RowMetadata metadata) {
        return new ModelCatalogEntry(
                PostgresBindingSupport.uuid(row, "provider_id"),
                PostgresBindingSupport.uuid(row, "model_id"),
                PostgresBindingSupport.string(row, "provider_key"),
                PostgresBindingSupport.string(row, "provider_display_name"),
                PostgresBindingSupport.string(row, "model_key"),
                PostgresBindingSupport.string(row, "model_display_name"),
                PostgresBindingSupport.integer(row, "context_window_tokens"),
                PostgresBindingSupport.integer(row, "max_output_tokens"),
                PostgresBindingSupport.bool(row, "provider_supports_streaming"),
                PostgresBindingSupport.bool(row, "provider_supports_cancellation"));
    }
}
