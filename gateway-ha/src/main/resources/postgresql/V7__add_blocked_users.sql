CREATE TABLE IF NOT EXISTS blocked_users (
    username VARCHAR(255) NOT NULL,
    scope_type VARCHAR(32) NOT NULL DEFAULT 'GLOBAL',
    scope_value VARCHAR(255) NOT NULL DEFAULT '*',
    reason VARCHAR(1024),
    blocked_by VARCHAR(255),
    blocked_datetime_utc TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (username, scope_type, scope_value)
);
