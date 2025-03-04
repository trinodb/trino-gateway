CREATE TABLE IF NOT EXISTS routing_rules (
    name varchar(128) primary key,
    description varchar(256),
    priority INT,
    conditionExpression varchar(512),
    actions varchar(1024),
    routingRuleEngine varchar(128)
)


