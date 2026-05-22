CREATE INDEX IF NOT EXISTS idx_inference_request_conversation_active
    ON inference_request (conversation_id, status, created_at DESC)
    WHERE status IN ('ACCEPTED', 'STREAMING');
