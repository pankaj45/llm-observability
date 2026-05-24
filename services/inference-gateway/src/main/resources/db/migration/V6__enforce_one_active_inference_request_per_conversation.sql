CREATE UNIQUE INDEX IF NOT EXISTS ux_inference_request_one_active_per_conversation
    ON inference_request (conversation_id)
    WHERE status IN ('ACCEPTED', 'STREAMING');
