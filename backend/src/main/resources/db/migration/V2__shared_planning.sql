CREATE TABLE club_session (
    id UUID PRIMARY KEY,
    title VARCHAR(100) NOT NULL,
    session_date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    coach_id UUID NOT NULL REFERENCES cap_user(id),
    location VARCHAR(120) NOT NULL,
    capacity INTEGER NOT NULL CHECK (capacity BETWEEN 1 AND 200),
    session_type VARCHAR(20) NOT NULL CHECK (session_type IN ('Groep', 'Privé', 'Open gym')),
    category VARCHAR(40) NOT NULL,
    program VARCHAR(3000) NOT NULL,
    CHECK (end_time > start_time)
);
CREATE INDEX club_session_date ON club_session(session_date, start_time);
CREATE TABLE session_recipient (
    session_id UUID NOT NULL REFERENCES club_session(id) ON DELETE CASCADE,
    athlete_id UUID NOT NULL REFERENCES cap_user(id),
    PRIMARY KEY (session_id, athlete_id)
);
CREATE TABLE session_booking (
    session_id UUID NOT NULL REFERENCES club_session(id) ON DELETE CASCADE,
    athlete_id UUID NOT NULL REFERENCES cap_user(id),
    cancelled BOOLEAN NOT NULL DEFAULT FALSE,
    attendance VARCHAR(16) NOT NULL DEFAULT 'Onbekend' CHECK (attendance IN ('Onbekend', 'Aanwezig', 'Afwezig')),
    PRIMARY KEY (session_id, athlete_id)
);
