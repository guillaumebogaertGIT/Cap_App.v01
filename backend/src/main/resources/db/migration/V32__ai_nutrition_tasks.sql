CREATE TABLE ai_nutrition_task (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES cap_user(id),
    payload TEXT NOT NULL DEFAULT '{}',
    revision INTEGER NOT NULL DEFAULT 0,
    processing_id UUID,
    locked_until TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP + INTERVAL '24 hours'
);
CREATE TABLE ai_nutrition_turn (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL REFERENCES ai_nutrition_task(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES cap_user(id),
    result TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
