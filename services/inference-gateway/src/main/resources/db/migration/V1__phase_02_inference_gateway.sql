CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS llm_provider (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_key TEXT NOT NULL UNIQUE,
    display_name TEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT true,
    supports_streaming BOOLEAN NOT NULL DEFAULT true,
    supports_cancellation BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS llm_model (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_id UUID NOT NULL REFERENCES llm_provider(id),
    model_key TEXT NOT NULL,
    display_name TEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT true,
    context_window_tokens INTEGER NOT NULL CHECK (context_window_tokens > 0),
    max_output_tokens INTEGER NOT NULL CHECK (max_output_tokens > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (provider_id, model_key)
);

CREATE TABLE IF NOT EXISTS conversation (
    id UUID PRIMARY KEY,
    tenant_id TEXT NOT NULL,
    project_id TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('ACTIVE', 'CANCELLED')),
    title TEXT NOT NULL,
    title_source TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    cancelled_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS conversation_message (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL REFERENCES conversation(id),
    tenant_id TEXT NOT NULL,
    project_id TEXT NOT NULL,
    role TEXT NOT NULL CHECK (role IN ('SYSTEM', 'USER', 'ASSISTANT', 'TOOL')),
    sequence INTEGER NOT NULL CHECK (sequence >= 0),
    content TEXT NOT NULL,
    content_hash TEXT NOT NULL,
    estimated_tokens INTEGER NOT NULL CHECK (estimated_tokens >= 0),
    redaction_state TEXT NOT NULL CHECK (redaction_state IN ('NONE', 'REDACTED')),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (conversation_id, sequence, role)
);

CREATE TABLE IF NOT EXISTS inference_request (
    id UUID PRIMARY KEY,
    tenant_id TEXT NOT NULL,
    project_id TEXT NOT NULL,
    conversation_id UUID NOT NULL REFERENCES conversation(id),
    provider_id UUID NOT NULL REFERENCES llm_provider(id),
    model_id UUID NOT NULL REFERENCES llm_model(id),
    provider_key TEXT NOT NULL,
    model_key TEXT NOT NULL,
    idempotency_key TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('ACCEPTED', 'STREAMING', 'COMPLETED', 'CANCELLED', 'FAILED')),
    streaming BOOLEAN NOT NULL DEFAULT true,
    request_metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    input_message_count INTEGER NOT NULL CHECK (input_message_count > 0),
    input_content_hash TEXT NOT NULL,
    output_content_hash TEXT,
    redis_stream_key TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    first_token_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    failed_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (tenant_id, project_id, idempotency_key)
);

CREATE TABLE IF NOT EXISTS inference_usage (
    id UUID PRIMARY KEY,
    inference_request_id UUID NOT NULL REFERENCES inference_request(id),
    input_tokens INTEGER NOT NULL CHECK (input_tokens >= 0),
    output_tokens INTEGER NOT NULL CHECK (output_tokens >= 0),
    total_tokens INTEGER NOT NULL CHECK (total_tokens >= 0),
    provider_reported_units TEXT NOT NULL,
    estimated_cost_amount NUMERIC(18, 8) NOT NULL DEFAULT 0,
    estimated_cost_currency TEXT NOT NULL DEFAULT 'USD',
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS inference_error (
    id UUID PRIMARY KEY,
    inference_request_id UUID NOT NULL REFERENCES inference_request(id),
    failure_stage TEXT NOT NULL,
    error_code TEXT NOT NULL,
    provider_error_code TEXT,
    message TEXT NOT NULL,
    retryable BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS inference_cancellation (
    id UUID PRIMARY KEY,
    inference_request_id UUID NOT NULL REFERENCES inference_request(id),
    requested_by TEXT NOT NULL,
    reason TEXT NOT NULL,
    provider_cancellation_attempted BOOLEAN NOT NULL,
    provider_cancellation_succeeded BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    resolved_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_conversation_tenant_project_created
    ON conversation (tenant_id, project_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_conversation_message_conversation_sequence
    ON conversation_message (conversation_id, sequence ASC);

CREATE INDEX IF NOT EXISTS idx_inference_request_conversation_created
    ON inference_request (conversation_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_inference_request_status_created
    ON inference_request (status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_inference_usage_request
    ON inference_usage (inference_request_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_inference_error_request
    ON inference_error (inference_request_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_inference_cancellation_request
    ON inference_cancellation (inference_request_id, created_at DESC);

INSERT INTO llm_provider (provider_key, display_name, enabled, supports_streaming, supports_cancellation)
VALUES ('gemini', 'Google Gemini', true, true, false)
ON CONFLICT (provider_key) DO UPDATE
SET display_name = EXCLUDED.display_name,
    enabled = EXCLUDED.enabled,
    supports_streaming = EXCLUDED.supports_streaming,
    supports_cancellation = EXCLUDED.supports_cancellation,
    updated_at = now();

INSERT INTO llm_model (provider_id, model_key, display_name, enabled, context_window_tokens, max_output_tokens)
SELECT provider.id, model.model_key, model.display_name, true, model.context_window_tokens, model.max_output_tokens
FROM llm_provider provider
CROSS JOIN (
    VALUES
        ('gemini-1.5-flash', 'Gemini 1.5 Flash', 1048576, 8192),
        ('gemini-1.5-pro', 'Gemini 1.5 Pro', 2097152, 8192)
) AS model(model_key, display_name, context_window_tokens, max_output_tokens)
WHERE provider.provider_key = 'gemini'
ON CONFLICT (provider_id, model_key) DO UPDATE
SET display_name = EXCLUDED.display_name,
    enabled = EXCLUDED.enabled,
    context_window_tokens = EXCLUDED.context_window_tokens,
    max_output_tokens = EXCLUDED.max_output_tokens,
    updated_at = now();
