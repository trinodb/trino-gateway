CREATE TABLE IF NOT EXISTS oauth2_routing (
oauth_id VARCHAR(256) PRIMARY KEY,
backend_url VARCHAR (256),
created bigint
);
CREATE INDEX IF NOT EXISTS oauth2_routing_created_idx ON oauth2_routing(created);
