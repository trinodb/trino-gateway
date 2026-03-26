CREATE TABLE gateway_backend (
    name VARCHAR(256) PRIMARY KEY,
    routing_group VARCHAR (256),
    backend_url VARCHAR (256),
    external_url VARCHAR (256),
    active NUMBER(1)
);

CREATE TABLE query_history (
    query_id VARCHAR(256) PRIMARY KEY,
    query_text VARCHAR (256),
    created NUMBER,
    backend_url VARCHAR (256),
    user_name VARCHAR(256),
    source VARCHAR(256)
);
CREATE INDEX query_history_created_idx ON query_history(created);
