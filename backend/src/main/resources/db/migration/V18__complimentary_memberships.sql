CREATE TABLE complimentary_membership (
    id UUID PRIMARY KEY,
    athlete_id UUID NOT NULL REFERENCES cap_user(id),
    offer_code VARCHAR(60) NOT NULL,
    starts_on DATE NOT NULL,
    ends_on DATE NOT NULL,
    reason VARCHAR(500) NOT NULL,
    granted_by UUID NOT NULL REFERENCES cap_user(id),
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    CHECK(ends_on>=starts_on)
);
CREATE INDEX complimentary_access ON complimentary_membership(athlete_id,starts_on,ends_on);
