ALTER TABLE oh_group DROP COLUMN IF EXISTS rule_group_ids;

CREATE TABLE oh_group_rule_group_ids
(
    oh_group_id   UUID         NOT NULL REFERENCES oh_group (id) ON DELETE CASCADE,
    rule_group_id VARCHAR(255) NOT NULL,
    PRIMARY KEY (oh_group_id, rule_group_id)
);
