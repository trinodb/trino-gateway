CREATE TABLE IF NOT EXISTS routing_rules (
    name VARCHAR(256) PRIMARY KEY,
    description TEXT,
    priority INTEGER NOT NULL,
    actions TEXT NOT NULL,
    rule_condition TEXT NOT NULL
);
