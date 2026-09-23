-- Deleting outdated groups now proceeds even when they are still linked to a service,
-- routing every such group through deleteAllLinksByGroup (WHERE group_id = ?). Without an
-- index on group_id, each call is a full scan of service_oh_group.
CREATE INDEX idx_service_oh_group_group_id ON service_oh_group (group_id);
