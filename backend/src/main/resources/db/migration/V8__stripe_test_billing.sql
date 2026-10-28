-- Billing never changes account roles or existing feature permissions.
CREATE TABLE billing_account (
    user_id UUID PRIMARY KEY REFERENCES cap_user(id),
    customer_id VARCHAR(255) UNIQUE,
    customer_key UUID NOT NULL,
    customer_started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    checkout_key UUID,
    checkout_price VARCHAR(255),
    checkout_started_at TIMESTAMP WITH TIME ZONE,
    checkout_session_id VARCHAR(255),
    synced_at TIMESTAMP WITH TIME ZONE
);
CREATE TABLE billing_subscription (
    subscription_id VARCHAR(255) PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES billing_account(user_id),
    customer_id VARCHAR(255) NOT NULL,
    price_id VARCHAR(255) NOT NULL,
    status VARCHAR(40) NOT NULL,
    payment_status VARCHAR(40) NOT NULL,
    period_start TIMESTAMP WITH TIME ZONE,
    period_end TIMESTAMP WITH TIME ZONE,
    cancel_at_period_end BOOLEAN NOT NULL,
    cancel_at TIMESTAMP WITH TIME ZONE,
    canceled_at TIMESTAMP WITH TIME ZONE,
    ended_at TIMESTAMP WITH TIME ZONE,
    synced_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX billing_subscription_owner ON billing_subscription(user_id);
CREATE TABLE billing_event (
    event_id VARCHAR(255) PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    user_id UUID NOT NULL REFERENCES billing_account(user_id),
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL
);
