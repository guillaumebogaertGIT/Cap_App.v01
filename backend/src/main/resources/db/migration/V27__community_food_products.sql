-- Previously personal entries remain private; only explicit new submissions are shared.
CREATE TABLE community_food_product (
    barcode VARCHAR(14) PRIMARY KEY,
    contributor_id UUID NOT NULL REFERENCES cap_user(id),
    payload TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX community_food_contributor ON community_food_product(contributor_id);
