package com.llmobservability.platform.ingestionworker.adapter.out.postgres;

import com.llmobservability.platform.ingestionworker.application.port.out.ProcessedEventRepository;
import com.llmobservability.platform.ingestionworker.domain.model.EventSource;
import com.llmobservability.platform.ingestionworker.domain.model.LifecycleEvent;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Repository
class PostgresProcessedEventRepository implements ProcessedEventRepository {
    private final DatabaseClient databaseClient;

    PostgresProcessedEventRepository(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<Boolean> tryStart(LifecycleEvent event, EventSource source) {
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql("""
                        INSERT INTO ingestion_processed_event (
                            event_id, dedupe_key, event_name, schema_version, tenant_id, project_id, correlation_id,
                            source_topic, source_partition, source_offset, processing_status, received_at
                        ) VALUES (
                            :eventId, :dedupeKey, :eventName, :schemaVersion, :tenantId, :projectId, :correlationId,
                            :sourceTopic, :sourcePartition, :sourceOffset, 'PROCESSING', :receivedAt
                        )
                        ON CONFLICT (dedupe_key) DO UPDATE
                        SET processing_status = 'PROCESSING',
                            failure_message = NULL,
                            source_topic = EXCLUDED.source_topic,
                            source_partition = EXCLUDED.source_partition,
                            source_offset = EXCLUDED.source_offset,
                            received_at = EXCLUDED.received_at,
                            processed_at = NULL
                        WHERE ingestion_processed_event.processing_status = 'FAILED'
                        """)
                .bind("eventId", UUID.fromString(event.eventId()))
                .bind("dedupeKey", event.dedupeKey())
                .bind("eventName", event.eventName())
                .bind("schemaVersion", event.schemaVersion())
                .bind("tenantId", event.tenantId())
                .bind("projectId", event.projectId())
                .bind("correlationId", event.correlationId())
                .bind("receivedAt", Instant.now());
        spec = PostgresBindingSupport.bindNullable(spec, "sourceTopic", source.topic(), String.class);
        spec = PostgresBindingSupport.bindNullable(spec, "sourcePartition", source.partition(), Integer.class);
        spec = PostgresBindingSupport.bindNullable(spec, "sourceOffset", source.offset(), Long.class);
        return spec.fetch()
                .rowsUpdated()
                .map(rows -> rows > 0);
    }

    @Override
    public Mono<Void> markProcessed(LifecycleEvent event) {
        return databaseClient.sql("""
                        UPDATE ingestion_processed_event
                        SET processing_status = 'PROCESSED',
                            processed_at = :processedAt,
                            failure_message = NULL
                        WHERE dedupe_key = :dedupeKey
                        """)
                .bind("processedAt", Instant.now())
                .bind("dedupeKey", event.dedupeKey())
                .fetch()
                .rowsUpdated()
                .then();
    }

    @Override
    public Mono<Void> markFailed(LifecycleEvent event, Throwable error) {
        String message = error.getMessage();
        if (message != null && message.length() > 500) {
            message = message.substring(0, 500);
        }
        return databaseClient.sql("""
                        UPDATE ingestion_processed_event
                        SET processing_status = 'FAILED',
                            processed_at = :processedAt,
                            failure_message = :failureMessage
                        WHERE dedupe_key = :dedupeKey
                        """)
                .bind("processedAt", Instant.now())
                .bind("failureMessage", message == null ? error.getClass().getSimpleName() : message)
                .bind("dedupeKey", event.dedupeKey())
                .fetch()
                .rowsUpdated()
                .then();
    }
}
