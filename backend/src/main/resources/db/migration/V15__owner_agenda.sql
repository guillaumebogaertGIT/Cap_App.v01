CREATE TABLE owner_task (
    id UUID PRIMARY KEY,
    title VARCHAR(160) NOT NULL,
    notes VARCHAR(2000) NOT NULL,
    due_date DATE NOT NULL,
    done BOOLEAN NOT NULL DEFAULT FALSE,
    version INTEGER NOT NULL DEFAULT 0,
    created_by UUID NOT NULL REFERENCES cap_user(id),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX owner_task_date ON owner_task(due_date);
CREATE TABLE owner_task_assignee (
    task_id UUID NOT NULL REFERENCES owner_task(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES cap_user(id),
    PRIMARY KEY(task_id,user_id)
);
CREATE TABLE owner_workout_event (
    id UUID PRIMARY KEY,
    athlete_id UUID NOT NULL REFERENCES cap_user(id),
    session_id VARCHAR(100) NOT NULL,
    workout_name VARCHAR(300) NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE(athlete_id,session_id)
);
CREATE INDEX owner_workout_event_time ON owner_workout_event(completed_at);
CREATE TABLE owner_event_read (
    event_id UUID NOT NULL REFERENCES owner_workout_event(id),
    user_id UUID NOT NULL REFERENCES cap_user(id),
    PRIMARY KEY(event_id,user_id)
);
