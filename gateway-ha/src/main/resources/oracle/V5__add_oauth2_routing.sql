CREATE TABLE oauth2_routing (
    pin_key CHAR(64) PRIMARY KEY,
    backend_url VARCHAR (256),
    created NUMBER
);
CREATE INDEX oauth2_routing_created_idx ON oauth2_routing(created);
