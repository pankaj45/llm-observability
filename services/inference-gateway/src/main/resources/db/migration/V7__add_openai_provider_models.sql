INSERT INTO llm_provider (provider_key, display_name, enabled, supports_streaming, supports_cancellation)
VALUES ('openai', 'OpenAI', true, true, false)
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
        ('gpt-5.5', 'GPT-5.5', 1000000, 128000),
        ('gpt-5.4', 'GPT-5.4', 1000000, 128000)
) AS model(model_key, display_name, context_window_tokens, max_output_tokens)
WHERE provider.provider_key = 'openai'
ON CONFLICT (provider_id, model_key) DO UPDATE
SET display_name = EXCLUDED.display_name,
    enabled = EXCLUDED.enabled,
    context_window_tokens = EXCLUDED.context_window_tokens,
    max_output_tokens = EXCLUDED.max_output_tokens,
    updated_at = now();
