ALTER TABLE billing_account ADD COLUMN checkout_offer VARCHAR(40);
ALTER TABLE billing_subscription ADD COLUMN offer_code VARCHAR(40);
ALTER TABLE billing_subscription ADD COLUMN term_end TIMESTAMP WITH TIME ZONE;

-- Committed before Checkout. A retry or webhook always refers to the same order.
CREATE TABLE billing_order (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES billing_account(user_id),
    offer_code VARCHAR(40) NOT NULL,
    price_id VARCHAR(255) NOT NULL,
    session_id VARCHAR(255) UNIQUE,
    subscription_id VARCHAR(255) UNIQUE,
    status VARCHAR(40) NOT NULL DEFAULT 'pending',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    paid_at TIMESTAMP WITH TIME ZONE,
    period_end TIMESTAMP WITH TIME ZONE
);
CREATE INDEX billing_order_owner ON billing_order(user_id, status);
CREATE TABLE lesson_credit (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES cap_user(id),
    order_id UUID NOT NULL REFERENCES billing_order(id),
    credit_number INTEGER NOT NULL,
    session_id UUID REFERENCES club_session(id) ON DELETE SET NULL,
    UNIQUE(order_id, credit_number),
    UNIQUE(user_id, session_id)
);
CREATE INDEX lesson_credit_balance ON lesson_credit(user_id, session_id);
