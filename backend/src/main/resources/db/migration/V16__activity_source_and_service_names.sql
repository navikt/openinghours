ALTER TABLE activity
    ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'MANUAL';

CREATE TABLE activity_service_name
(
    activity_id UUID NOT NULL,
    name        VARCHAR(200) NOT NULL,
    PRIMARY KEY (activity_id, name),
    FOREIGN KEY (activity_id) REFERENCES activity (id) ON DELETE CASCADE
);
