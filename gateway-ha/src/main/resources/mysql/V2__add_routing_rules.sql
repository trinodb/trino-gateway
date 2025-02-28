CREATE TABLE IF NOT EXISTS routing_rules (
    name varchar(128) primary key,
    description varchar(256),
    priority INT,
    conditionExpression varchar(256),
    actions varchar(256),
    routingRuleEngine varchar(128)
)


