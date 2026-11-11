CREATE TABLE meal_plan_revision (
    plan_id UUID NOT NULL REFERENCES meal_plan(id), version INTEGER NOT NULL,
    athlete_id UUID REFERENCES cap_user(id), week_start DATE, payload TEXT NOT NULL,
    saved_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY(plan_id,version)
);
INSERT INTO meal_plan_revision(plan_id,version,athlete_id,week_start,payload,saved_at)
SELECT id,version,athlete_id,week_start,payload,updated_at FROM meal_plan;
CREATE INDEX meal_plan_revision_client ON meal_plan_revision(athlete_id,saved_at DESC);
CREATE TABLE meal_plan_assignment (
    id UUID PRIMARY KEY, plan_id UUID NOT NULL REFERENCES meal_plan(id),
    athlete_id UUID NOT NULL REFERENCES cap_user(id), start_date DATE NOT NULL, end_date DATE NOT NULL,
    payload TEXT NOT NULL, assigned_by UUID NOT NULL REFERENCES cap_user(id),
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK(end_date>=start_date)
);
CREATE INDEX meal_plan_assignment_client_dates ON meal_plan_assignment(athlete_id,start_date,end_date);
