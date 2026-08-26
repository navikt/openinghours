-- Remove duplicate (name, type) rows, keeping only the most recently created one.
DELETE FROM service
WHERE id NOT IN (
    SELECT DISTINCT ON (name, type) id
    FROM service
    ORDER BY name, type, created_at DESC
);

ALTER TABLE service ADD CONSTRAINT uq_service_name_type UNIQUE (name, type);