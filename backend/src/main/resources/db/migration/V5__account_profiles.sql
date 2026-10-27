CREATE TABLE account_profile (
    user_id UUID PRIMARY KEY REFERENCES cap_user(id) ON DELETE CASCADE,
    values_json TEXT NOT NULL,
    photo BYTEA
);
