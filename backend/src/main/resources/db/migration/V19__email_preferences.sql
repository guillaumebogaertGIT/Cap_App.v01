CREATE TABLE email_preferences (
    user_id UUID PRIMARY KEY REFERENCES cap_user(id),
    assignments BOOLEAN NOT NULL DEFAULT TRUE,
    reminders BOOLEAN NOT NULL DEFAULT TRUE
);
