CREATE TABLE IF NOT EXISTS ingestion_processed_event (
    event_id UUID PRIMARY KEY,
    dedupe_key TEXT NOT NULL UNIQUE,
    event_name TEXT NOT NULL,
    schema_version TEXT NOT NULL,
    tenant_id TEXT NOT NULL,
    project_id TEXT NOT NULL,
    correlation_id TEXT NOT NULL,
    source_topic TEXT,
    source_partition INTEGER,
    source_offset BIGINT,
    processing_status TEXT NOT NULL CHECK (processing_status IN ('PROCESSING', 'PROCESSED', 'FAILED')),
    failure_message TEXT,
    received_at TIMESTAMPTZ NOT NULL,
    processed_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_ingestion_processed_event_status_received
    ON ingestion_processed_event (processing_status, received_at DESC);
