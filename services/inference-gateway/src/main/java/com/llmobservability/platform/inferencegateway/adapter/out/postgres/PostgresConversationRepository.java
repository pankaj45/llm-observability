package com.llmobservability.platform.inferencegateway.adapter.out.postgres;

import com.llmobservability.platform.inferencegateway.application.port.out.ConversationRepository;
import com.llmobservability.platform.inferencegateway.domain.model.Conversation;
import com.llmobservability.platform.inferencegateway.domain.model.ConversationStatus;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

import static com.llmobservability.platform.inferencegateway.adapter.out.postgres.PostgresBindingSupport.bindNullable;

@Repository
class PostgresConversationRepository implements ConversationRepository {
    private final DatabaseClient databaseClient;

    PostgresConversationRepository(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<Conversation> save(Conversation conversation) {
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql("""
                        INSERT INTO conversation (
                            id, tenant_id, project_id, status, title, title_source, created_at, updated_at, cancelled_at
                        ) VALUES (
                            :id, :tenantId, :projectId, :status, :title, :titleSource, :createdAt, :updatedAt, :cancelledAt
                        )
                        """)
                .bind("id", conversation.id())
                .bind("tenantId", conversation.tenantId())
                .bind("projectId", conversation.projectId())
                .bind("status", conversation.status().name())
                .bind("title", conversation.title())
                .bind("titleSource", conversation.titleSource())
                .bind("createdAt", conversation.createdAt())
                .bind("updatedAt", conversation.updatedAt());
        return bindNullable(spec, "cancelledAt", conversation.cancelledAt(), Instant.class)
                .fetch()
                .rowsUpdated()
                .thenReturn(conversation);
    }

    @Override
    public Mono<Conversation> findById(UUID conversationId) {
        return databaseClient.sql("""
                        SELECT id, tenant_id, project_id, status, title, title_source, created_at, updated_at, cancelled_at
                        FROM conversation
                        WHERE id = :id
                        """)
                .bind("id", conversationId)
                .map((row, metadata) -> new Conversation(
                        PostgresBindingSupport.uuid(row, "id"),
                        PostgresBindingSupport.string(row, "tenant_id"),
                        PostgresBindingSupport.string(row, "project_id"),
                        ConversationStatus.valueOf(PostgresBindingSupport.string(row, "status")),
                        PostgresBindingSupport.string(row, "title"),
                        PostgresBindingSupport.string(row, "title_source"),
                        PostgresBindingSupport.instant(row, "created_at"),
                        PostgresBindingSupport.instant(row, "updated_at"),
                        PostgresBindingSupport.instant(row, "cancelled_at")))
                .one();
    }
}
