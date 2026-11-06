CREATE TABLE chat_preferences(user_id UUID PRIMARY KEY REFERENCES cap_user(id),discoverable BOOLEAN NOT NULL DEFAULT FALSE);
CREATE TABLE social_message(
    id UUID PRIMARY KEY,
    author_id UUID NOT NULL REFERENCES cap_user(id),
    recipient_id UUID REFERENCES cap_user(id),
    session_id UUID REFERENCES club_session(id) ON DELETE CASCADE,
    kind VARCHAR(12) NOT NULL CHECK(kind IN ('community','direct','group')),
    content VARCHAR(2000) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CHECK((kind='community' AND recipient_id IS NULL AND session_id IS NULL) OR (kind='direct' AND recipient_id IS NOT NULL AND session_id IS NULL) OR (kind='group' AND recipient_id IS NULL AND session_id IS NOT NULL))
);
CREATE INDEX social_feed ON social_message(kind,created_at);
CREATE INDEX social_direct ON social_message(author_id,recipient_id,created_at);
CREATE INDEX social_group ON social_message(session_id,created_at);
