CREATE TABLE exercise_state (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    payload TEXT NOT NULL
);
INSERT INTO exercise_state (id, payload) VALUES (1, '[]');
