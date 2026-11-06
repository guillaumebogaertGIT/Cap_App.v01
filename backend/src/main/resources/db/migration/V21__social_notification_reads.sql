CREATE TABLE social_notification_read (
    message_id UUID NOT NULL REFERENCES social_message(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES cap_user(id),
    PRIMARY KEY(message_id,user_id)
);
