-- Manual flag marking opening hours that may lead to periods of instability.
-- Set explicitly by users through the rule API; never derived from the rule DSL.
-- Purely informational: it never filters which rules are returned by the API.
ALTER TABLE rule ADD COLUMN unstable_opening_hours BOOLEAN NOT NULL DEFAULT false;
