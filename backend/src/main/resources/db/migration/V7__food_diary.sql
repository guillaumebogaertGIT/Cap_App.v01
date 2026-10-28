CREATE TABLE food_diary_entry (
    id UUID PRIMARY KEY,
    athlete_id UUID NOT NULL REFERENCES cap_user(id),
    entry_date DATE NOT NULL,
    payload TEXT NOT NULL,
    saved_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX food_diary_athlete_day ON food_diary_entry(athlete_id, entry_date);
