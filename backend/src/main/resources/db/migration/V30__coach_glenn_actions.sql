CREATE TABLE ai_coach_settings (
    user_id UUID PRIMARY KEY REFERENCES cap_user(id),
    payload TEXT NOT NULL,
    version INTEGER NOT NULL DEFAULT 1
);
CREATE TABLE ai_coach_action (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES cap_user(id),
    payload TEXT NOT NULL,
    result TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP + INTERVAL '24 hours'
);
