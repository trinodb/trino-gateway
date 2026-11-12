CREATE TABLE routing_rules (
    name VARCHAR2(256) PRIMARY KEY,
    description CLOB,
    priority NUMBER NOT NULL,
    actions CLOB NOT NULL,
    rule_condition CLOB NOT NULL
);
