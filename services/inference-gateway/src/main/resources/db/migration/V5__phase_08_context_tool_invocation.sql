CREATE TABLE IF NOT EXISTS context_tool_invocation (
    id UUID PRIMARY KEY,
    tenant_id TEXT NOT NULL,
    project_id TEXT NOT NULL,
    conversation_id UUID NOT NULL REFERENCES conversation(id),
    inference_request_id UUID NOT NULL REFERENCES inference_request(id),
    tool_name TEXT NOT NULL,
    tool_provider TEXT NOT NULL,
    input_hash TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('COMPLETED', 'FAILED', 'SKIPPED')),
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ NOT NULL,
    latency_ms BIGINT NOT NULL CHECK (latency_ms >= 0),
    cache_hit BOOLEAN NOT NULL DEFAULT false,
    result_count INTEGER NOT NULL CHECK (result_count >= 0),
    source_urls JSONB NOT NULL DEFAULT '[]'::jsonb,
    error_code TEXT,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX IF NOT EXISTS idx_context_tool_invocation_request
    ON context_tool_invocation (inference_request_id, started_at DESC);

CREATE INDEX IF NOT EXISTS idx_context_tool_invocation_tenant_project
    ON context_tool_invocation (tenant_id, project_id, started_at DESC);
