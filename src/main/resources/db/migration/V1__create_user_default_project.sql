CREATE TABLE user_default_project (
                                      user_id        VARCHAR(128) PRIMARY KEY,
                                      project_key    VARCHAR(128) NOT NULL,
                                      project_name   VARCHAR(256) NOT NULL,
                                      updated_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_user_default_project_updated_at ON user_default_project(updated_at);
