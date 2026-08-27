-- Manual flag marking opening hours that may cause periods of instability.
-- Set explicitly by users through the rule API; never derived from the rule DSL.
ALTER TABLE rule ADD COLUMN unstable_opening_hours BOOLEAN NOT NULL DEFAULT false;
