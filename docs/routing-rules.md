# Routing rules

Trino Gateway includes a routing rules engine.

By default, Trino Gateway reads the `X-Trino-Routing-Group` request header to
route requests. If this header is not specified, requests are sent to default
routing group (adhoc).

The routing rules engine feature enables you to either write custom logic to route
requests based on the request info such as any of the [request
headers](https://trino.io/docs/current/develop/client-protocol.html#client-request-headers),
or set a URL address to make an HTTP POST request and route based on the returned result.

Routing rules are separated from Trino Gateway application code to a
configuration file or a separate service. This separate service is specified as a URL
and can implement any dynamic rule changes or other behavior.

### Enabling the routing rules engine

To enable the routing rules engine, find the following lines in `gateway-ha-config.yml`.

* Set `rulesEngineEnabled` to `true`, then `rulesType` as `FILE` or `EXTERNAL`.
* Then either add `rulesConfigPath` to the path to your rules config file or set `rulesExternalConfiguration`
  to the URL of an external service for routing rules processing.
  *`rulesType` is by default `FILE` unless specified.

```yaml
routingRules:
    rulesEngineEnabled: true
    rulesType: FILE
    rulesConfigPath: "app/config/routing_rules.yml" # replace with actual path to your rules config file
    rulesExternalConfiguration:
        urlPath: https://router.example.com/gateway-rules # replace with your own API path
        blacklistHeaders:
            - 'Authorization'
```

* Redirect URLs are not supported
* Optionally add headers to the `BlacklistHeaders` list to exclude requests with corresponding header values
  from being sent in the POST request.

If there is error parsing the routing rules configuration file, an error is logged,
and requests are routed using the routing group header `X-Trino-Routing-Group` as default.

### Use an external service for routing rules

You can use an external service for processing your routing by setting the
`rulesType` to `EXTERNAL` and configuring the `rulesExternalConfiguration`.

Trino Gateway then sends all headers as a map in the body of a POST request to the external service.
Headers specified in `blacklistHeaders` are excluded. If `requestAnalyzerConfig.analyzeRequest` is set to `true`, 
`TrinoRequestUser` and `TrinoQueryProperties` are also included. 

Additionally, the following HTTP information is included:

- `remoteUser`
- `method`
- `requestURI`
- `queryString`
- `session`
- `remoteAddr`
- `remoteHost`
- `parameterMap`

The external service can process the information in any way desired 
and must return a result with the following criteria:

* Response status code of OK (200)
* Message in JSON format
* Only one group can be returned
* If errors is not null, then query would route to default routing group adhoc 

```json
{
    "routingGroup": "test-group",
    "errors": [
        "Error1",
        "Error2",
        "Error3"
    ]
}
```

### Configure routing rules with a file

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
object called `request`. Rules may also utilize
[trinoRequestUser](#trinorequestuser) and
[trinoQueryProperties](#trinoqueryproperties)
objects, which provide information about the user and query respectively.
There should be at least one action of the form
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

### TrinoRequestUser

This class attempts to extract the user from a request. In order, it attempts

1. The `X-Trino-User` header
2. The `Authorization: Basic` header
3. The `Authorization: Bearer` header. Requires configuring an OAuth2 User Info URL
4. The `Trino-UI-Token` or `__Secure-Trino-ID-Token` cookie

Kerberos and Certificate authentication are not currently supported. If the 
request contains the `Authorization: Bearer` header, an attempt will be made to
treat the token as a JWT and deserialize it. If this is successful, the 
value of the claim named in `requestAnalyzerConfig.tokenUserField` is used as
the username. By default, this is the `email` claim. If the token is not a valid
JWT, and `requestAnalyzerConfig.oauthTokenInfoUrl` is configured, then the token
will be exchanged with the Info URL. Responses are cached for 10 minutes to
avoid triggering rate limits. 

You may call `trinoRequestUser.getUser()` and `trinoRequestUser.getUserInfo()`
in your routing rules. If a user was not successfully extracted,
`trinoRequestUser.getUser()` will return an empty
[Optional](https://docs.oracle.com/javase/8/docs/api/java/util/Optional.html).
`trinoRequestUser.getUserInfo()` will return an
[Optional\<UserInfo\>](https://www.javadoc.io/doc/com.nimbusds/oauth2-oidc-sdk/5.34/com/nimbusds/openid/connect/sdk/claims/UserInfo.html)
if a token is successfully exchanged with the `oauthTokenInfoUrl`, and an empty
`Optional` otherwise.

`trinoRequestUser.userExistsAndEquals("usernameToTest")` can be used to check a
username against the extracted user. It will return `False` if a user has not
been extracted.

User extraction is only available if enabled by configuring
`requestAnalyzerConfig.analyzeRequest = True`

### TrinoQueryProperties

This class attempts to parse the body of a request as SQL. Note that only a
syntactic analysis is performed! If a query œreferences a view, then that
view will not be expanded, and tables referenced by the view will not be
recognized. Note that Views and Materialized Views are treated as tables and
added to the list of tables in all contexts, including  statements such as
`CREATE VIEW`.

A routing rule can call the following methods on the `trinoQueryProperties`
object:

* `boolean isNewQuerySubmission()`: is the request a POST to the `v1/statement`
  query endpoint.
* `boolean isQueryParsingSuccessful()`: was the request successfully parsed. 
* `String getQueryType()`: the class name of the `Statement`, e.g. `ShowCreate`.
  Note that these are not mapped to the `ResourceGroup` query types. For a full
  list of potential query types, see the classes in
  [STATEMENT_QUERY_TYPES](https://github.com/trinodb/trino/blob/2a882933937427e28ea0a3906ab13a60bcb4faad/core/trino-main/src/main/java/io/trino/util/StatementUtils.java#L170)
* `String getResourceGroupQueryType()`: the Resource Group query type, for
  example `SELECT`, `DATA_DEFINITION`. For a full list see [queryType in
  the Trino documentation](https://trino.io/docs/current/admin/resource-groups.html#selector-rules)
* `String getDefaultCatalog()`: the default catalog, if set. It may or may not
  be referenced in the actual SQL
* `String getDefaultSchema()`: the default schema,  if set. It may or may not
  be referenced in the actual SQL
* `Set<String> getCatalogs()`: the set of catalogs used in the query. Includes
  the default catalog if used by a non-fully qualified table reference
* `Set<String> getSchemas()`: the set of schemas used in the query. Includes the
  default schema if used by a non-fully qualified table reference
* `Set<String> getCatalogSchemas()` the set of qualified schemas used in the
  query, in the form `catalog.schema`
* `boolean tablesContains(String testName)` returns true if the query contains a
  reference to the table `testName`.`testName` should be fully qualified, for
  example `testcat.testschema.testtable`
* `Set<QualifiedName> getTables()`: the set of tables used in the query. These
  are fully qualified, any partially qualified table reference in the SQL
  will be qualified by the default catalog and schema.
* `String getBody()`: the raw request body

### Configuration

The `trinoQueryProperties`  are configured under the `requestAnalyzerConfig`
configuration  node.

#### analyzeRequest

Set to `True` to make `trinoQueryProperties` and `trinoRequestUser` available

#### maxBodySize

By default, the max body size is 1,000,000 characters. This can be modified by
configuring `maxBodySize`. If the request body is greater or equal to this 
limit, Trino Gateway will not process the query. A buffer of length 
`maxBodySize` will be allocated per query, so reduce this value if you observe
excessive GC. `maxBodySize` cannot be set to values larger than 2**31-1, the 
maximum size of a Java String.

#### isClientsUseV2Format

Some commercial extensions to Trino use the V2 Request Structure
[V2 style request structure](https://github.com/trinodb/trino/wiki/Trino-v2-client-protocol#submit-a-query). Support for V2-style requests can be enabled
by setting this property to true. If you use a commercial version of Trino, ask
your vendor how to set this configuration. 

#### tokenUserField

When extracting the user from a JWT token, this field is used as the username.
By default, the `email` claim is used. 
 
#### oauthTokenInfoUrl

If configured, then Trino will attempt to retrieve user info by exchanging
potential authorization tokens with this URL. Responses are cached for 10
minutes to avoid triggering rate limits.

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
    else if (request.getHeader(\"X-Trino-Client-Tags\") contains \"label=bar\") {
      result.put(\"routingGroup\", \"etl-bar\")
    }
    else {
      result.put(\"routingGroup\", \"etl\")
    }"
```
