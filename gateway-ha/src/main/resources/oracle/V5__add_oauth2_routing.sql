CREATE TABLE oauth2_routing (
    oauth_id VARCHAR(256) PRIMARY KEY,
    backend_url VARCHAR (256),
    created NUMBER
);
CREATE INDEX oauth2_routing_created_idx ON oauth2_routing(created);
