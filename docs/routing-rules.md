**trino-gateway documentation**

<table>
  <tr>
    <td><b><a href="design.md">Design</a></b></td>
    <td><a href="development.md">Development</a></td>
    <td><a href="security.md">Security</a></td>
    <td><a href="operation.md">Operation</a></td>
    <td><a href="gateway-api.md">Gateway API</a></td>
    <td><a href="resource-groups-api.md">Resource groups API</a></td>
    <td><b><a href="routing-rules.md">Routing rules</a></b></td>
    <td><a href="references.md">References</a></td>
  </tr>
</table>

# Routing rules

Trino Gateway includes a routing rules engine.

By default, trino-gateway reads the `X-Trino-Routing-Group` request header to
route requests. If this header is not specified, requests are sent to default
routing group (adhoc).

The routing rules engine feature enables you to write custom logic to route
requests based on the request info such as any of the [request
headers](https://trino.io/docs/current/develop/client-protocol.html#client-request-headers).
Routing rules are separated from trino-gateway application code to a
configuration file, allowing for dynamic rule changes.

### Defining your routing rules

To express and fire routing rules, we use the
[easy-rules](https://github.com/j-easy/easy-rules) engine. These rules should be
stored in a YAML file. Rules consist of a name, description, condition, and list
of actions. If the condition of a particular rule evaluates to true, its actions
are fired.

```yaml
---
name: "airflow"
description: "if query from airflow, route to etl group"
condition: 'request.getHeader("X-Trino-Source") == "airflow"'
actions:
  - 'result.put("routingGroup", "etl")'
---
name: "airflow special"
description: "if query from airflow with special label, route to etl-special group"
condition: 'request.getHeader("X-Trino-Source") == "airflow" && request.getHeader("X-Trino-Client-Tags") contains "label=special"'
actions:
  - 'result.put("routingGroup", "etl-special")'
```

In the condition, you can access the methods of a
[HttpServletRequest](https://docs.oracle.com/javaee/6/api/javax/servlet/http/HttpServletRequest.html)
object called `request`. There should be at least one action of the form
`result.put(\"routingGroup\", \"foo\")` which says that if a request satisfies
the condition, it should be routed to `foo`.

The condition and actions are written in [MVEL](http://mvel.documentnode.com/),
an expression language with Java-like syntax. In most cases, users can write
their conditions/actions in Java syntax and expect it to work. There are some
MVEL-specific operators that could be useful though. For example, instead of
doing a null-check before accessing the `String.contains` method like this:

```yaml
condition: 'request.getHeader("X-Trino-Client-Tags") != null && request.getHeader("X-Trino-Client-Tags").contains("label=foo")'
```

You can use the `contains` operator

```yaml
condition: 'request.getHeader("X-Trino-Client-Tags") contains "label=foo"'
```

If no rules match, then request is routed to adhoc.

### Execution of Rules

All rules whose conditions are satisfied will fire. For example, in the
"airflow" and "airflow special" example rules given above, a query with source
`airflow` and label `special` will satisfy both rules. The `routingGroup` is set
to `etl` and then to `etl-special` because of the order in which the rules of
defined. If we swap the order of the rules, then we would possibly get `etl`
instead, which is undesirable.

One could solve this by writing the rules such that they're atomic (any query
will match exactly one rule). For example we can change the first rule to

```yaml
---
name: "airflow"
description: "if query from airflow, route to etl group"
condition: 'request.getHeader("X-Trino-Source") == "airflow" && request.getHeader("X-Trino-Client-Tags") == null'
actions:
  - 'result.put("routingGroup", "etl")'
---
```

This could be hard to maintain as we add more rules. To have better control over
the execution of rules, we could use rule priorities and composite rules.
Overall, with priorities, composite rules, and the constructs that MVEL support,
you should likely be able to express your routing logic.

#### Rule Priority

We can assign an integer value `priority` to a rule. The lower this integer is,
the earlier it will fire. If the priority is not specified, the priority is
defaulted to INT_MAX. We can add priorities to our airflow and airflow special
rule like so:

```yaml
---
name: "airflow"
description: "if query from airflow, route to etl group"
priority: 0
condition: 'request.getHeader("X-Trino-Source") == "airflow"'
actions:
  - 'result.put("routingGroup", "etl")'
---
name: "airflow special"
description: "if query from airflow with special label, route to etl-special group"
priority: 1
condition: 'request.getHeader("X-Trino-Source") == "airflow" && request.getHeader("X-Trino-Client-Tags") contains "label=special"'
actions:
  - 'result.put("routingGroup", "etl-special")'
```

Note that both rules will still fire. The difference is that we've guaranteed
that the first rule (priority 0) is fired before the second rule (priority 1).
Thus `routingGroup` is set to `etl` and then to `etl-special`, so the
`routingGroup` will always be `etl-special` in the end.

Above, the more specific rules have less priority since we want them to be the
last to set `routingGroup`. This is a little counterintuitive. To further
control the execution of rules, for example to have only one rule fire, we can
use composite rules.

##### Composite Rules

First, please refer to easy-rule composite rules docs:
https://github.com/j-easy/easy-rules/wiki/defining-rules#composite-rules

Above, we saw how to control the order of rule execution using priorities. In
addition to this, we could have only the first rule matched to be fired (the
highest priority one) and the rest ignored. We can use `ActivationRuleGroup` to
achieve this.

```yaml
---
name: "airflow rule group"
description: "routing rules for query from airflow"
compositeRuleType: "ActivationRuleGroup"
composingRules:
  - name: "airflow special"
    description: "if query from airflow with special label, route to etl-special group"
    priority: 0
    condition: 'request.getHeader("X-Trino-Source") == "airflow" && request.getHeader("X-Trino-Client-Tags") contains "label=special"'
    actions:
      - 'result.put("routingGroup", "etl-special")'
  - name: "airflow"
    description: "if query from airflow, route to etl group"
    priority: 1
    condition: 'request.getHeader("X-Trino-Source") == "airflow"'
    actions:
      - 'result.put("routingGroup", "etl")'
```

Note that the priorities have switched. The more specific rule has a higher
priority, since we want it to be fired first. A query coming from airflow with
special label is matched to the "airflow special" rule first, since it's higher
priority, and the second rule is ignored. A query coming from airflow with no
labels does not match the first rule, and is then tested and matched to the
second rule.

We can also use `ConditionalRuleGroup` and `ActivationRuleGroup` to implement an
if/else workflow. The following logic in pseudocode:

```
if source == "airflow":
  if clientTags["label"] == "foo":
    return "etl-foo"
  else if clientTags["label"] = "bar":
    return "etl-bar"
  else
    return "etl"
```

Can be implemented with these rules:

```yaml
name: "airflow rule group"
description: "routing rules for query from airflow"
compositeRuleType: "ConditionalRuleGroup"
composingRules:
  - name: "main condition"
    description: "source is airflow"
    priority: 0 # rule with the highest priority acts as main condition
    condition: 'request.getHeader("X-Trino-Source") == "airflow"'
    actions:
      - ""
  - name: "airflow subrules"
    compositeRuleType: "ActivationRuleGroup" # use ActivationRuleGroup to simulate if/else
    composingRules:
      - name: "label foo"
        description: "label client tag is foo"
        priority: 0
        condition: 'request.getHeader("X-Trino-Client-Tags") contains "label=foo"'
        actions:
          - 'result.put("routingGroup", "etl-foo")'
      - name: "label bar"
        description: "label client tag is bar"
        priority: 0
        condition: 'request.getHeader("X-Trino-Client-Tags") contains "label=bar"'
        actions:
          - 'result.put("routingGroup", "etl-bar")'
      - name: "airflow default"
        description: "airflow queries default to etl"
        condition: "true"
        actions:
          - 'result.put("routingGroup", "etl")'
```

##### If statements (MVEL Flow Control)

Above, we saw how we can use `ConditionalRuleGroup` and `ActivationRuleGroup` to
implement and `if/else` workflow. We could also take advantage of the fact that
MVEL supports `if` statements and other flow control (loops, etc). The following
logic in pseudocode:

```
if source == "airflow":
  if clientTags["label"] == "foo":
    return "etl-foo"
  else if clientTags["label"] = "bar":
    return "etl-bar"
  else
    return "etl"
```

Can be implemented with these rules:

```yaml
---
name: "airflow rules"
description: "if query from airflow"
condition: "request.getHeader(\"X-Trino-Source\") == \"airflow\""
actions:
  - "if (request.getHeader(\"X-Trino-Client-Tags\") contains \"label=foo\") {
      result.put(\"routingGroup\", \"etl-foo\")
    }
    else "if (request.getHeader(\"X-Trino-Client-Tags\") contains \"label=bar\") {
      result.put(\"routingGroup\", \"etl-bar\")
    }
    else {
      result.put(\"routingGroup\", \"etl\")
    }"
```

### Enabling routing rules engine

To enable routing rules engine, find the following lines in
`gateway-ha-config.yml`. Set `rulesEngineEnabled` to True and `rulesConfigPath`
to the path to your rules config file.

```
routingRules:
  rulesEngineEnabled: true
  rulesConfigPath: "src/test/resources/rules/routing_rules.yml" # replace with path to your rules config file
```

