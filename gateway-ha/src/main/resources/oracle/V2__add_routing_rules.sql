CREATE TABLE routing_rules (
    name varchar(128),
    description varchar(512),
    priority int,
    conditionExpression varchar(512),
    actions varchar(1024),
    routingRuleEngine varchar(128),
    PRIMARY KEY (name)
);
