CREATE TABLE client_tariff (
    user_id UUID PRIMARY KEY REFERENCES cap_user(id),
    cents BIGINT NOT NULL CHECK (cents BETWEEN 0 AND 10000000),
    period VARCHAR(12) NOT NULL CHECK (period IN ('month','quarter','year','session','total')),
    version INTEGER NOT NULL DEFAULT 1,
    updated_by UUID NOT NULL REFERENCES cap_user(id),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
