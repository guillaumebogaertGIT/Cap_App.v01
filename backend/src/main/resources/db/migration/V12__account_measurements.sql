CREATE TABLE account_measurement (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES cap_user(id),
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL,
    source VARCHAR(16) NOT NULL,
    values_json TEXT NOT NULL
);
CREATE INDEX account_measurement_history ON account_measurement(user_id,recorded_at);
