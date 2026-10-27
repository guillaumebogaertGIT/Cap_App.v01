-- Keep existing domain snapshots intact while transactions protect shared writes.
CREATE TABLE workout_state (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    payload TEXT NOT NULL
);
INSERT INTO workout_state (id, payload) VALUES (1, '{"workouts":[],"assignments":[]}');
CREATE TABLE athlete_training (
    athlete_id UUID PRIMARY KEY REFERENCES cap_user(id),
    payload TEXT NOT NULL DEFAULT '[]'
);
INSERT INTO athlete_training (athlete_id) SELECT id FROM cap_user WHERE role = 'ATHLETE';
ALTER TABLE club_session ADD COLUMN workout_snapshot TEXT;
CREATE TABLE training_draft (
    id UUID PRIMARY KEY,
    athlete_id UUID NOT NULL UNIQUE REFERENCES cap_user(id),
    payload TEXT NOT NULL
);
