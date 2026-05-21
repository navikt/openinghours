-- Enforce 1:1 relationship: a service can only be linked to one opening-hours group.
-- Remove extra links if any exist, keeping the most recently created one.
DELETE FROM service_oh_group
WHERE ctid NOT IN (
    SELECT DISTINCT ON (service_id) ctid
FROM service_oh_group
ORDER BY service_id, created_at DESC
    );

-- Drop the composite PK and replace with a UNIQUE constraint on service_id
ALTER TABLE service_oh_group DROP CONSTRAINT service_oh_group_pkey;
ALTER TABLE service_oh_group ADD PRIMARY KEY (service_id);