ALTER TABLE social_message ADD COLUMN channel VARCHAR(24);
UPDATE social_message SET channel='general' WHERE kind='community';
ALTER TABLE social_message ADD CONSTRAINT social_channel_scope CHECK (
    (kind='community' AND channel IN ('general','hyrox','hybrid','move','lift-hit','open-gym') AND channel IS NOT NULL)
    OR (kind<>'community' AND channel IS NULL)
);
CREATE INDEX social_community_channel ON social_message(channel,created_at) WHERE kind='community';
