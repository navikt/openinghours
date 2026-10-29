-- Scheduled activities (maintenance, deployments, etc.) covering one or more services. They are
-- kept separate from opening-hours rules: they never affect isOpen, and a service can have any
-- number of (possibly overlapping) activities on the same day.

CREATE TABLE activity
(
    id                          UUID         NOT NULL,
    header                      VARCHAR(200) NOT NULL,
    message                     text         NOT NULL,
    start_at                    timestamp with time zone NOT NULL,
    end_at                      timestamp with time zone NOT NULL,
    only_show_for_nav_employees boolean      NOT NULL DEFAULT false,
    created_at                  timestamp with time zone NOT NULL DEFAULT NOW(),
    updated_at                  timestamp with time zone NULL,
    PRIMARY KEY (id),
    CONSTRAINT chk_activity_period CHECK (end_at > start_at)
);

CREATE INDEX idx_activity_period ON activity (start_at, end_at);

-- Activities whose only service is deleted are removed by ServiceService.delete; links to
-- other services are removed by the cascade below.
CREATE TABLE activity_service
(
    activity_id UUID NOT NULL,
    service_id  UUID NOT NULL,
    PRIMARY KEY (activity_id, service_id),
    FOREIGN KEY (activity_id) REFERENCES activity (id) ON DELETE CASCADE,
    FOREIGN KEY (service_id) REFERENCES service (id) ON DELETE CASCADE
);

CREATE INDEX idx_activity_service_service_id ON activity_service (service_id);
