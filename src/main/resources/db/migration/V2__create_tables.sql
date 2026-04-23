CREATE TABLE rule
(
    id          UUID         NOT NULL,
    name        VARCHAR(100) NOT NULL,
    rule        VARCHAR(100)  NOT NULL,
    header      VARCHAR(200) NOT NULL,
    text        VARCHAR(200) NOT NULL,
    only_show_for_nav_employees boolean NOT NULL,
    created_at  timestamp with time zone  NOT NULL DEFAULT NOW(),
    updated_at  timestamp with time zone NULL,
    PRIMARY KEY (id)
);

CREATE TABLE rule_group
(
    id                      UUID         NOT NULL,
    name                    VARCHAR(100) NOT NULL,
    rule_group_ids          VARCHAR ARRAY  NULL,
    created_at  timestamp with time zone  NOT NULL DEFAULT NOW(),
    updated_at  timestamp with time zone NULL,
    PRIMARY KEY (id)
);
