CREATE TABLE cap_user (
    id UUID PRIMARY KEY,
    email VARCHAR(254) NOT NULL UNIQUE,
    display_name VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(16) NOT NULL CHECK (role IN ('COACH', 'ATHLETE')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT normalized_email CHECK (email = LOWER(email))
);

CREATE TABLE athlete_invitation (
    token_hash VARCHAR(64) PRIMARY KEY,
    email VARCHAR(254) NOT NULL UNIQUE,
    invited_by UUID NOT NULL REFERENCES cap_user(id),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    accepted_at TIMESTAMP WITH TIME ZONE
);
