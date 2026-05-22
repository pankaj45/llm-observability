ALTER TABLE conversation_message
    DROP COLUMN IF EXISTS tenant_id,
    DROP COLUMN IF EXISTS project_id;
