CREATE TABLE oh_group
(
    id             UUID         NOT NULL,
    name           VARCHAR(100) NOT NULL,
    rule_group_ids VARCHAR ARRAY NULL,
    created_at     timestamp with time zone NOT NULL DEFAULT NOW(),
    updated_at     timestamp with time zone NULL,
    PRIMARY KEY (id)
);

CREATE TABLE service
(
    id          UUID         NOT NULL,
    name        VARCHAR(100) NOT NULL,
    type        VARCHAR(20)  NOT NULL,
    team        VARCHAR(100) NOT NULL,
    monitorlink VARCHAR(300) NULL,
    logglink    VARCHAR(300) NULL,
    description text         NULL,
    created_at  timestamp with time zone NOT NULL DEFAULT NOW(),
    updated_at  timestamp with time zone NULL,
    PRIMARY KEY (id)
);

CREATE TABLE service_oh_group
(
    service_id UUID NOT NULL,
    group_id   UUID NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT NOW(),
    updated_at timestamp with time zone NULL,
    PRIMARY KEY (service_id, group_id),
    FOREIGN KEY (service_id) REFERENCES service (id),
    FOREIGN KEY (group_id) REFERENCES oh_group (id)
);

CREATE TABLE service_opening_hours
(
    id              UUID NOT NULL,
    service_id      UUID NOT NULL,
    day_of_the_week INT  NOT NULL,
    opening_time    TIME NOT NULL,
    closing_time    TIME NOT NULL,
    created_at      timestamp with time zone NOT NULL DEFAULT NOW(),
    updated_at      timestamp with time zone NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (service_id) REFERENCES service (id),
    CONSTRAINT valid_weekday_c1 CHECK (day_of_the_week <= 6),
    CONSTRAINT valid_weekday_c2 CHECK (day_of_the_week >= 0)
);

CREATE TABLE service_status
(
    id            UUID          NOT NULL,
    service_id    UUID          NOT NULL,
    status        VARCHAR(20)   NOT NULL,
    response_time integer       NULL,
    description   VARCHAR(1000) NULL,
    logglink      VARCHAR(100)  NULL,
    source        VARCHAR(20)   NOT NULL DEFAULT 'UNKNOWN',
    created_at    timestamp with time zone NOT NULL DEFAULT NOW(),
    updated_at    timestamp with time zone NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (service_id) REFERENCES service (id)
);

CREATE TABLE service_status_delta
(
    id            UUID          NOT NULL,
    service_id    UUID          NOT NULL,
    status        VARCHAR(20)   NOT NULL,
    description   VARCHAR(1000) NULL,
    logglink      VARCHAR(100)  NULL,
    response_time integer       NULL,
    counter       integer       NOT NULL DEFAULT 0,
    active        boolean       NOT NULL DEFAULT true,
    created_at    timestamp with time zone NOT NULL DEFAULT NOW(),
    updated_at    timestamp with time zone NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (service_id) REFERENCES service (id)
);
