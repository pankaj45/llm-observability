package com.llmobservability.platform.inferencegateway.adapter.out.postgres;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.llmobservability.platform.inferencegateway.application.port.out.ConversationContextSnapshotRepository;
import com.llmobservability.platform.inferencegateway.domain.model.ConversationContextSnapshot;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
class PostgresConversationContextSnapshotRepository implements ConversationContextSnapshotRepository {
    private final DatabaseClient databaseClient;
    private final ObjectMapper objectMapper;

    PostgresConversationContextSnapshotRepository(DatabaseClient databaseClient, ObjectMapper objectMapper) {
        this.databaseClient = databaseClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> save(ConversationContextSnapshot snapshot) {
        return databaseClient.sql("""
                        INSERT INTO conversation_context_snapshot (
                            id, conversation_id, source_start_sequence, source_end_sequence,
                            summary_content, summary_content_hash, estimated_tokens,
                            compaction_strategy, provider_key, model_key, created_by_request_id,
                            metadata, created_at
                        ) VALUES (
                            :id, :conversationId, :sourceStartSequence, :sourceEndSequence,
                            :summaryContent, :summaryContentHash, :estimatedTokens,
                            :compactionStrategy, :providerKey, :modelKey, :createdByRequestId,
                            CAST(:metadata AS jsonb), :createdAt
                        )
                        """)
                .bind("id", snapshot.id())
                .bind("conversationId", snapshot.conversationId())
                .bind("sourceStartSequence", snapshot.sourceStartSequence())
                .bind("sourceEndSequence", snapshot.sourceEndSequence())
                .bind("summaryContent", snapshot.summaryContent())
                .bind("summaryContentHash", snapshot.summaryContentHash())
                .bind("estimatedTokens", snapshot.estimatedTokens())
                .bind("compactionStrategy", snapshot.compactionStrategy())
                .bind("providerKey", snapshot.providerKey())
                .bind("modelKey", snapshot.modelKey())
                .bind("createdByRequestId", snapshot.createdByRequestId())
                .bind("metadata", PostgresBindingSupport.json(objectMapper, snapshot.metadata()))
                .bind("createdAt", snapshot.createdAt())
                .fetch()
                .rowsUpdated()
                .then();
    }

    @Override
    public Mono<ConversationContextSnapshot> findLatest(UUID conversationId, String providerKey, String modelKey) {
        return databaseClient.sql("""
                        SELECT id, conversation_id, source_start_sequence, source_end_sequence,
                               summary_content, summary_content_hash, estimated_tokens,
                               compaction_strategy, provider_key, model_key, created_by_request_id,
                               metadata::text AS metadata, created_at
                        FROM conversation_context_snapshot
                        WHERE conversation_id = :conversationId
                          AND provider_key = :providerKey
                          AND model_key = :modelKey
                        ORDER BY source_end_sequence DESC, created_at DESC
                        LIMIT 1
                        """)
                .bind("conversationId", conversationId)
                .bind("providerKey", providerKey)
                .bind("modelKey", modelKey)
                .map((row, metadata) -> map(row))
                .one();
    }

    private ConversationContextSnapshot map(io.r2dbc.spi.Row row) {
        return new ConversationContextSnapshot(
                PostgresBindingSupport.uuid(row, "id"),
                PostgresBindingSupport.uuid(row, "conversation_id"),
                PostgresBindingSupport.integer(row, "source_start_sequence"),
                PostgresBindingSupport.integer(row, "source_end_sequence"),
                PostgresBindingSupport.string(row, "summary_content"),
                PostgresBindingSupport.string(row, "summary_content_hash"),
                PostgresBindingSupport.integer(row, "estimated_tokens"),
                PostgresBindingSupport.string(row, "compaction_strategy"),
                PostgresBindingSupport.string(row, "provider_key"),
                PostgresBindingSupport.string(row, "model_key"),
                PostgresBindingSupport.uuid(row, "created_by_request_id"),
                PostgresBindingSupport.stringMap(objectMapper, PostgresBindingSupport.string(row, "metadata")),
                PostgresBindingSupport.instant(row, "created_at"));
    }
}
