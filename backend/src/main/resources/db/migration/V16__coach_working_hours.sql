CREATE TABLE coach_shift (
    id UUID PRIMARY KEY,
    coach_id UUID NOT NULL REFERENCES cap_user(id),
    shift_date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    note VARCHAR(500) NOT NULL,
    version INTEGER NOT NULL DEFAULT 0,
    CHECK (end_time > start_time)
);
CREATE INDEX coach_shift_schedule ON coach_shift(coach_id,shift_date,start_time);
