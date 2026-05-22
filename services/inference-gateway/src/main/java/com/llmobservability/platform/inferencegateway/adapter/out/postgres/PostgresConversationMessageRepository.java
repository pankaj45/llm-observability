package com.llmobservability.platform.inferencegateway.adapter.out.postgres;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.llmobservability.platform.inferencegateway.application.port.out.ConversationMessageRepository;
import com.llmobservability.platform.inferencegateway.domain.model.ConversationMessage;
import com.llmobservability.platform.inferencegateway.domain.model.MessageRole;
import com.llmobservability.platform.inferencegateway.domain.model.RedactionState;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@Repository
class PostgresConversationMessageRepository implements ConversationMessageRepository {
    private final DatabaseClient databaseClient;
    private final ObjectMapper objectMapper;

    PostgresConversationMessageRepository(DatabaseClient databaseClient, ObjectMapper objectMapper) {
        this.databaseClient = databaseClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> saveAll(List<ConversationMessage> messages) {
        return Flux.fromIterable(messages)
                .flatMap(this::save)
                .then();
    }

    @Override
    public Mono<Void> save(ConversationMessage message) {
        return databaseClient.sql("""
                        INSERT INTO conversation_message (
                            id, conversation_id, role, sequence, content, content_hash,
                            estimated_tokens, redaction_state, metadata, created_at
                        ) VALUES (
                            :id, :conversationId, :role, :sequence, :content, :contentHash,
                            :estimatedTokens, :redactionState, CAST(:metadata AS jsonb), :createdAt
                        )
                        """)
                .bind("id", message.id())
                .bind("conversationId", message.conversationId())
                .bind("role", message.role().name())
                .bind("sequence", message.sequence())
                .bind("content", message.content())
                .bind("contentHash", message.contentHash())
                .bind("estimatedTokens", message.estimatedTokens())
                .bind("redactionState", message.redactionState().name())
                .bind("metadata", PostgresBindingSupport.json(objectMapper, message.metadata()))
                .bind("createdAt", message.createdAt())
                .fetch()
                .rowsUpdated()
                .then();
    }

    @Override
    public Flux<ConversationMessage> findByConversationId(UUID conversationId) {
        return databaseClient.sql("""
                        SELECT id, conversation_id, role, sequence, content, content_hash,
                               estimated_tokens, redaction_state, metadata::text AS metadata, created_at
                        FROM conversation_message
                        WHERE conversation_id = :conversationId
                        ORDER BY sequence ASC, created_at ASC
                        """)
                .bind("conversationId", conversationId)
                .map((row, metadata) -> map(row))
                .all();
    }

    @Override
    public Flux<ConversationMessage> findByConversationIdAfterSequence(UUID conversationId, int afterSequence, int limit) {
        return databaseClient.sql("""
                        SELECT id, conversation_id, role, sequence, content, content_hash,
                               estimated_tokens, redaction_state, metadata::text AS metadata, created_at
                        FROM conversation_message
                        WHERE conversation_id = :conversationId
                          AND sequence > :afterSequence
                        ORDER BY sequence ASC, created_at ASC
                        LIMIT :limit
                        """)
                .bind("conversationId", conversationId)
                .bind("afterSequence", afterSequence)
                .bind("limit", limit)
                .map((row, metadata) -> map(row))
                .all();
    }

    @Override
    public Mono<Long> countByConversationId(UUID conversationId) {
        return databaseClient.sql("""
                        SELECT COUNT(*) AS message_count
                        FROM conversation_message
                        WHERE conversation_id = :conversationId
                        """)
                .bind("conversationId", conversationId)
                .map((row, metadata) -> row.get("message_count", Long.class))
                .one();
    }

    @Override
    public Mono<ConversationMessage> findLatestByConversationId(UUID conversationId) {
        return databaseClient.sql("""
                        SELECT id, conversation_id, role, sequence, content, content_hash,
                               estimated_tokens, redaction_state, metadata::text AS metadata, created_at
                        FROM conversation_message
                        WHERE conversation_id = :conversationId
                        ORDER BY sequence DESC, created_at DESC
                        LIMIT 1
                        """)
                .bind("conversationId", conversationId)
                .map((row, metadata) -> map(row))
                .one();
    }

    private ConversationMessage map(io.r2dbc.spi.Row row) {
        return new ConversationMessage(
                PostgresBindingSupport.uuid(row, "id"),
                PostgresBindingSupport.uuid(row, "conversation_id"),
                MessageRole.valueOf(PostgresBindingSupport.string(row, "role")),
                PostgresBindingSupport.integer(row, "sequence"),
                PostgresBindingSupport.string(row, "content"),
                PostgresBindingSupport.string(row, "content_hash"),
                PostgresBindingSupport.integer(row, "estimated_tokens"),
                RedactionState.valueOf(PostgresBindingSupport.string(row, "redaction_state")),
                PostgresBindingSupport.stringMap(objectMapper, PostgresBindingSupport.string(row, "metadata")),
                PostgresBindingSupport.instant(row, "created_at"));
    }
}
