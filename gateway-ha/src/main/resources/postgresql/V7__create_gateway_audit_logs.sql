CREATE TABLE IF NOT EXISTS gateway_audit_logs (
    audit_id BIGSERIAL PRIMARY KEY,
    user_name VARCHAR(256) NOT NULL,
    ip_address VARCHAR(45),
    backend_name VARCHAR(256) NOT NULL,
    operation VARCHAR(64) NOT NULL,
    context VARCHAR(64) NOT NULL,
    success BOOLEAN NOT NULL,
    user_comment VARCHAR(1024),
    change_time TIMESTAMPTZ NOT NULL DEFAULT now()
);
