CREATE TABLE blocked_users (
    username VARCHAR(255) NOT NULL,
    scope_type VARCHAR(32) DEFAULT 'GLOBAL' NOT NULL,
    scope_value VARCHAR(255) DEFAULT '*' NOT NULL,
    reason VARCHAR(1024),
    blocked_by VARCHAR(255),
    blocked_datetime_utc TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY (username, scope_type, scope_value)
);
