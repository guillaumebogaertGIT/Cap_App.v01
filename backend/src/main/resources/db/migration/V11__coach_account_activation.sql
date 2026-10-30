-- Keep historical references intact when a coach leaves the club.
ALTER TABLE cap_user ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;
