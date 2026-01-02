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

CREATE TABLE resource_groups (
    resource_group_id NUMBER GENERATED ALWAYS as IDENTITY(START with 1 INCREMENT by 1),
    name VARCHAR(250) NOT NULL,
    -- OPTIONAL POLICY CONTROLS
    parent NUMBER,
    jmx_export CHAR(1),
    scheduling_policy VARCHAR(128),
    scheduling_weight NUMBER,
    -- REQUIRED QUOTAS
    soft_memory_limit VARCHAR(128) NOT NULL,
    max_queued INT NOT NULL,
    hard_concurrency_limit NUMBER NOT NULL,
    -- OPTIONAL QUOTAS
    soft_concurrency_limit NUMBER,
    soft_cpu_limit VARCHAR(128),
    hard_cpu_limit VARCHAR(128),
    environment VARCHAR(128),
    PRIMARY KEY(resource_group_id),
    FOREIGN KEY (parent) REFERENCES resource_groups (resource_group_id) ON DELETE CASCADE
);

CREATE TABLE selectors (
    resource_group_id NUMBER NOT NULL,
    priority NUMBER NOT NULL,
    -- Regex fields -- these will be used as a regular expression pattern to
    --                 match against the field of the same name on queries
    user_regex VARCHAR(512),
    source_regex VARCHAR(512),
    -- Selector fields -- these must match exactly.
    query_type VARCHAR(512),
    client_tags VARCHAR(512),
    selector_resource_estimate VARCHAR(1024),
    FOREIGN KEY (resource_group_id) REFERENCES resource_groups (resource_group_id) ON DELETE CASCADE
);

CREATE TABLE resource_groups_global_properties (
    name VARCHAR(128) NOT NULL PRIMARY KEY,
    value VARCHAR(512) NULL,
    CHECK (name in ('cpu_quota_period'))
);

CREATE TABLE exact_match_source_selectors(
    environment VARCHAR(256),
    update_time TIMESTAMP NOT NULL,
    -- Selector fields which must exactly match a query
    source VARCHAR(512) NOT NULL,
    query_type VARCHAR(512),
    resource_group_id VARCHAR(256) NOT NULL,
    PRIMARY KEY (environment, source, resource_group_id),
    UNIQUE (source, environment, query_type, resource_group_id)
);

CREATE TABLE gateway_audit_logs (
    audit_id NUMBER GENERATED ALWAYS as IDENTITY(START with 1 INCREMENT by 1),
    user_name VARCHAR(256) NOT NULL,
    ip_address VARCHAR2(45),
    backend_name VARCHAR(256) NOT NULL,
    operation VARCHAR(256) NOT NULL,
    context VARCHAR(256) NOT NULL,
    success NUMBER(1) NOT NULL CHECK (success IN (0,1)),
    user_comment VARCHAR(1024),
    change_time TIMESTAMP NOT NULL,
    PRIMARY KEY(audit_id)
);
