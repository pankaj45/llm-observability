-- Phase 07: Update model catalog for chatbot UI
-- Disable legacy 1.5 models that are no longer offered as the primary options.
-- Add gemini-2.5-flash and gemini-2.0-flash-lite as the enabled models.

-- Disable gemini-1.5-flash and gemini-1.5-pro (they remain in the catalog but are
-- no longer offered to new inference requests from the chatbot UI).
UPDATE llm_model
SET enabled = false
WHERE model_key IN ('gemini-1.5-flash', 'gemini-1.5-pro')
  AND provider_id = (SELECT id FROM llm_provider WHERE provider_key = 'gemini');

-- Upsert gemini-2.5-flash
INSERT INTO llm_model (provider_id, model_key, display_name, enabled, context_window_tokens, max_output_tokens)
SELECT provider.id,
       model.model_key,
       model.display_name,
       true,
       model.context_window_tokens,
       model.max_output_tokens
FROM llm_provider provider,
     (VALUES
          ('gemini-2.5-flash',    'Gemini 2.5 Flash',         1048576, 65536),
          ('gemini-2.0-flash-lite', 'Gemini 2.0 Flash Lite',  1048576, 8192)
     ) AS model(model_key, display_name, context_window_tokens, max_output_tokens)
WHERE provider.provider_key = 'gemini'
ON CONFLICT (provider_id, model_key) DO UPDATE
    SET display_name          = EXCLUDED.display_name,
        enabled               = EXCLUDED.enabled,
        context_window_tokens = EXCLUDED.context_window_tokens,
        max_output_tokens     = EXCLUDED.max_output_tokens;
