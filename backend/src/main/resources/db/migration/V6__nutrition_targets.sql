CREATE TABLE nutrition_target (
    athlete_id UUID PRIMARY KEY REFERENCES cap_user(id),
    coach_id UUID NOT NULL REFERENCES cap_user(id),
    calories DOUBLE PRECISION NOT NULL CHECK (calories >= 1),
    protein_g DOUBLE PRECISION NOT NULL CHECK (protein_g >= 0),
    carbs_g DOUBLE PRECISION NOT NULL CHECK (carbs_g >= 0),
    fat_g DOUBLE PRECISION NOT NULL CHECK (fat_g >= 0),
    water_ml DOUBLE PRECISION NOT NULL CHECK (water_ml >= 0),
    origin VARCHAR(30) NOT NULL,
    saved_at TIMESTAMP WITH TIME ZONE NOT NULL
);
