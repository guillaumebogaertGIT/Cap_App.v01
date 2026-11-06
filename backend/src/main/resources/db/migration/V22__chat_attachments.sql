CREATE TABLE social_attachment (
    message_id UUID PRIMARY KEY REFERENCES social_message(id) ON DELETE CASCADE,
    filename VARCHAR(180) NOT NULL,
    size_bytes INTEGER NOT NULL CHECK (size_bytes BETWEEN 1 AND 10485760),
    data BYTEA NOT NULL
);
