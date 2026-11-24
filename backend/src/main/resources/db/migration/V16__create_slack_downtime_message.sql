-- Raw messages fetched from the Slack downtime channel (#varsling-nedetid), together with the
-- LLM extraction result and the activities that were created from them.

CREATE TABLE slack_downtime_message
(
    id          UUID         NOT NULL,
    channel_id  VARCHAR(50)  NOT NULL,
    message_ts  VARCHAR(50)  NOT NULL,
    posted_at   timestamp with time zone NOT NULL,
    raw_text    text         NOT NULL,
    status      VARCHAR(30)  NOT NULL,
    extraction  text         NULL,
    reason      text         NULL,
    attempts    INTEGER      NOT NULL DEFAULT 0,
    created_at  timestamp with time zone NOT NULL DEFAULT NOW(),
    updated_at  timestamp with time zone NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_slack_downtime_message_channel_ts UNIQUE (channel_id, message_ts)
);

CREATE INDEX idx_slack_downtime_message_status ON slack_downtime_message (status);
CREATE INDEX idx_slack_downtime_message_channel_posted ON slack_downtime_message (channel_id, posted_at);

CREATE TABLE slack_downtime_message_activity
(
    message_id  UUID NOT NULL,
    activity_id UUID NOT NULL,
    PRIMARY KEY (message_id, activity_id),
    FOREIGN KEY (message_id) REFERENCES slack_downtime_message (id) ON DELETE CASCADE,
    FOREIGN KEY (activity_id) REFERENCES activity (id) ON DELETE CASCADE
);

CREATE INDEX idx_slack_downtime_message_activity_activity_id ON slack_downtime_message_activity (activity_id);
