CREATE TABLE IF NOT EXISTS gateway_backend (
name VARCHAR(256) PRIMARY KEY,
routing_group VARCHAR (256),
backend_url VARCHAR (256),
external_url VARCHAR (256),
active BOOLEAN
);

CREATE TABLE IF NOT EXISTS query_history (
query_id VARCHAR(256) PRIMARY KEY,
query_text VARCHAR (256),
created bigint,
backend_url VARCHAR (256),
user_name VARCHAR(256),
source VARCHAR(256)
);
CREATE INDEX query_history_created_idx ON query_history(created);

CREATE TABLE IF NOT EXISTS oauth2_routing (
pin_key CHAR(64) PRIMARY KEY,
backend_url VARCHAR (256),
created bigint
);
CREATE INDEX oauth2_routing_created_idx ON oauth2_routing(created);
