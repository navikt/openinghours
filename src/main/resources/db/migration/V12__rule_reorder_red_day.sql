-- Recreate the rule table to position red_day before created_at.
-- PostgreSQL does not support ALTER TABLE ... REORDER COLUMNS.

ALTER TABLE rule RENAME TO rule_old;

CREATE TABLE rule
(
    id                          UUID                     NOT NULL,
    name                        VARCHAR(100)             NOT NULL,
    rule                        VARCHAR(100)             NOT NULL,
    header                      VARCHAR(200)             NULL,
    text                        VARCHAR(200)             NULL,
    only_show_for_nav_employees BOOLEAN                  NOT NULL,
    red_day                     BOOLEAN                  NOT NULL DEFAULT false,
    created_at                  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMP WITH TIME ZONE NULL,
    PRIMARY KEY (id)
);

INSERT INTO rule (id, name, rule, header, text, only_show_for_nav_employees, red_day, created_at, updated_at)
SELECT id, name, rule, header, text, only_show_for_nav_employees, red_day, created_at, updated_at
FROM rule_old;

DROP TABLE rule_old;

-- Re-add named constraint now that the old table (and its constraint) is gone
ALTER TABLE rule ADD CONSTRAINT uq_rule_name UNIQUE (name);


