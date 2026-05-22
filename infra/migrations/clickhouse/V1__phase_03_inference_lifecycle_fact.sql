CREATE DATABASE IF NOT EXISTS llm_observability;

CREATE TABLE IF NOT EXISTS llm_observability.inference_lifecycle_fact (
    event_id UUID,
    event_name LowCardinality(String),
    schema_version LowCardinality(String),
    occurred_at DateTime64(3, 'UTC'),
    producer LowCardinality(String),
    tenant_id String,
    project_id String,
    correlation_id String,
    traceparent Nullable(String),
    request_id String,
    conversation_id String,
    provider LowCardinality(String),
    model LowCardinality(String),
    status LowCardinality(String),
    input_message_count Nullable(UInt32),
    input_content_hash Nullable(String),
    input_tokens Nullable(UInt32),
    output_tokens Nullable(UInt32),
    total_tokens Nullable(UInt32),
    duration_ms Nullable(UInt64),
    failure_stage Nullable(String),
    error_code Nullable(String),
    provider_error_code Nullable(String),
    retryable Nullable(Bool),
    cancellation_reason Nullable(String),
    provider_cancellation_attempted Nullable(Bool),
    provider_cancellation_succeeded Nullable(Bool),
    inserted_at DateTime64(3, 'UTC') DEFAULT now64(3)
) ENGINE = MergeTree
PARTITION BY toYYYYMM(occurred_at)
ORDER BY (tenant_id, project_id, occurred_at, request_id, event_name);
