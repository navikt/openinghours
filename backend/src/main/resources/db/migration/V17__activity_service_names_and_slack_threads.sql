-- Activities extracted from #varsling-nedetid often concern services that are not in the service
-- catalog (external services such as ID-porten, or broad changes such as all *.nav.no). The
-- affected service names are stored for every activity created from Slack, so an activity can
-- exist with names only. Service links are still used where possible, because they are what
-- places an activity in a service's own calendar.
CREATE TABLE activity_service_name
(
    activity_id UUID         NOT NULL,
    name        VARCHAR(200) NOT NULL,
    PRIMARY KEY (activity_id, name),
    FOREIGN KEY (activity_id) REFERENCES activity (id) ON DELETE CASCADE
);

-- Follow-up messages ("ferdig", "avlyst", "utsatt") only ever change activities created from
-- Slack. Editing an activity through the API turns it into a MANUAL activity.
ALTER TABLE activity ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'MANUAL';

UPDATE activity SET source = 'SLACK'
WHERE id IN (SELECT activity_id FROM slack_downtime_message_activity);

-- Thread replies are fetched as well; thread_ts points to the parent message.
ALTER TABLE slack_downtime_message ADD COLUMN thread_ts VARCHAR(50) NULL;

CREATE INDEX idx_slack_downtime_message_channel_thread ON slack_downtime_message (channel_id, thread_ts);
