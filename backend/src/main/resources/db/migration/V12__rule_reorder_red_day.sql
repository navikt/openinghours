-- No-op: column ordering is not semantically meaningful in PostgreSQL or JPA.
-- The red_day column is added correctly by V11 (ALTER TABLE ... ADD COLUMN).
-- This migration was originally a full table-recreation to reorder columns,
-- which is unnecessary and introduces avoidable operational risk in production.
SELECT 1;
