CREATE TABLE personal_food_product (
    user_id UUID NOT NULL REFERENCES cap_user(id),
    barcode VARCHAR(14) NOT NULL,
    payload TEXT NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, barcode)
);
