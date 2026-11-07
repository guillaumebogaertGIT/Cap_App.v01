CREATE TABLE ai_coach_budget (
    id INTEGER PRIMARY KEY CHECK(id=1),
    reserved_cents INTEGER NOT NULL DEFAULT 0 CHECK(reserved_cents BETWEEN 0 AND 500)
);
INSERT INTO ai_coach_budget(id) VALUES(1);
-- Metadata only: never store prompts, answers, API keys or health records here.
CREATE TABLE ai_coach_request (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES cap_user(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    state VARCHAR(16) NOT NULL CHECK(state IN ('reserved','completed','failed'))
);
CREATE INDEX ai_coach_request_user_time ON ai_coach_request(user_id,created_at);
