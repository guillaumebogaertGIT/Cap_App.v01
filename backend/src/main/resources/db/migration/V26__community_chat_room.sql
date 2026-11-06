-- Keep the existing general key/history; its display name becomes Announcements.
ALTER TABLE social_message DROP CONSTRAINT social_channel_scope;
ALTER TABLE social_message ADD CONSTRAINT social_channel_scope CHECK (
    (kind='community' AND channel IN ('general','chat-room','hyrox','hybrid','move','lift-hit','open-gym') AND channel IS NOT NULL)
    OR (kind<>'community' AND channel IS NULL)
);
