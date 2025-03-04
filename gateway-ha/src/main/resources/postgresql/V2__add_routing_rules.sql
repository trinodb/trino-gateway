CREATE TABLE IF NOT EXISTS routing_rules (
    name varchar primary key,
    description varchar,
    priority int,
    conditionExpression varchar,
    actions varchar[],
    routingRuleEngine varchar
);
