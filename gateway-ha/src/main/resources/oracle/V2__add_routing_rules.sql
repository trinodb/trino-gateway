CREATE TYPE string_array AS VARRAY(100) OF VARCHAR2(512);
CREATE TABLE IF NOT EXISTS routing_rules (
    name varchar primary key,
    description varchar,
    priority int,
    conditionExpression varchar,
    actions string_array,
    routingRuleEngine varchar
);
