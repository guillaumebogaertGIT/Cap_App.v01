CREATE TABLE nutrition_preference (
    athlete_id UUID PRIMARY KEY REFERENCES cap_user(id) ON DELETE CASCADE,
    allergies TEXT NOT NULL DEFAULT '',
    avoided_foods TEXT NOT NULL DEFAULT '',
    ai_share BOOLEAN NOT NULL DEFAULT FALSE,
    ai_consent_at TIMESTAMPTZ,
    ai_consent_notice TEXT,
    version INTEGER NOT NULL DEFAULT 1,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE nutrition_ai_consent_event (
    id UUID PRIMARY KEY,
    athlete_id UUID NOT NULL REFERENCES cap_user(id) ON DELETE CASCADE,
    granted BOOLEAN NOT NULL,
    notice_version VARCHAR(40) NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX nutrition_ai_consent_event_athlete ON nutrition_ai_consent_event(athlete_id, recorded_at);
