CREATE TABLE IF NOT EXISTS conversation_context_snapshot (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL REFERENCES conversation(id),
    source_start_sequence INTEGER NOT NULL CHECK (source_start_sequence >= 0),
    source_end_sequence INTEGER NOT NULL CHECK (source_end_sequence >= source_start_sequence),
    summary_content TEXT NOT NULL,
    summary_content_hash TEXT NOT NULL,
    estimated_tokens INTEGER NOT NULL CHECK (estimated_tokens >= 0),
    compaction_strategy TEXT NOT NULL,
    provider_key TEXT NOT NULL,
    model_key TEXT NOT NULL,
    created_by_request_id UUID NOT NULL REFERENCES inference_request(id),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (conversation_id, provider_key, model_key, source_start_sequence, source_end_sequence, compaction_strategy)
);

CREATE INDEX IF NOT EXISTS idx_conversation_context_snapshot_latest
    ON conversation_context_snapshot (conversation_id, provider_key, model_key, source_end_sequence DESC, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_conversation_context_snapshot_request
    ON conversation_context_snapshot (created_by_request_id);
