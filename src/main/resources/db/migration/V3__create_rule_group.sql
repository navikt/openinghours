CREATE TABLE rule_group
(
    id          UUID         NOT NULL,
    name        VARCHAR(100) NOT NULL,
    created_at  timestamp with time zone  NOT NULL DEFAULT NOW(),
    updated_at  timestamp with time zone NULL,
    PRIMARY KEY (id)
);

CREATE TABLE rule_group_ids
(
    group_id      UUID NOT NULL,
    rule_group_id UUID NOT NULL,
    FOREIGN KEY (group_id) REFERENCES rule_group (id)
);
