CREATE TABLE meal_plan (
    id UUID PRIMARY KEY,
    athlete_id UUID REFERENCES cap_user(id),
    week_start DATE,
    payload TEXT NOT NULL,
    published_payload TEXT,
    version INTEGER NOT NULL DEFAULT 1,
    updated_by UUID NOT NULL REFERENCES cap_user(id),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMPTZ,
    CHECK ((athlete_id IS NULL AND week_start IS NULL) OR (athlete_id IS NOT NULL AND week_start IS NOT NULL))
);
CREATE UNIQUE INDEX meal_plan_client_week ON meal_plan(athlete_id,week_start) WHERE athlete_id IS NOT NULL;
