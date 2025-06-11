# Routing rules

Trino Gateway includes a routing rules engine.

By default, Trino Gateway reads the `X-Trino-Routing-Group` request header to
route requests. If this header is not specified, requests are sent to the
default routing group called `adhoc`.

The routing rules engine feature enables you to either write custom logic to
route requests based on the request info such as any of the [request
headers](https://trino.io/docs/current/develop/client-protocol.html#client-request-headers),
or set a URL address to make an HTTP POST request and route based on the
returned result.

Routing rules are defined in a configuration file or implemented in separate,
custom service application. The connection to the separate service is configured
as a URL. It can implement any dynamic rule changes or other behavior.

### Enabling the routing rules engine

To enable the routing rules engine, find the following lines in
`config.yaml`:

* Set `rulesEngineEnabled` to `true`, then `rulesType` as `FILE` or `EXTERNAL`.
* If you set `rulesType: FILE`, then set `rulesConfigPath` to the path to your
  rules config file.
* The rules file will be re-read every minute by default. You may change this by setting
  `rulesRefreshPeriod: Duration`, where duration is an airlift style Duration such as `30s`.
* If you set `rulesType: EXTERNAL`, set `rulesExternalConfiguration` to the URL
  of an external service for routing rules processing.
* `rulesType` is by default `FILE` unless specified.

```yaml
routingRules:
    rulesEngineEnabled: true
    rulesType: FILE
    rulesConfigPath: "app/config/routing_rules.yml" # replace with actual path to your rules config file
    rulesExternalConfiguration:
        urlPath: https://router.example.com/gateway-rules # replace with your own API path
        excludeHeaders:
            - 'Authorization'
            - 'Accept-Encoding'
```

* Redirect URLs are not supported.
* Optionally, add headers to the `excludeHeaders` list to exclude requests with
  corresponding header values from being sent in the POST request.
* Check headers to exclude when making API requests, specifics depend on the
  network configuration.

If there is error parsing the routing rules configuration file, an error is
logged, and requests are routed using the routing group header
`X-Trino-Routing-Group` as default.

### Configuring API requests with HTTP client config

You can configure the HTTP client by adding the following configuration to
the `serverConfig:` section with the `router` prefix.

```yaml
serverConfig:
    router.http-client.request-timeout: 1s
```

Please refer to the [Trino HTTP client properties](
https://trino.io/docs/current/admin/properties-http-client.html)
documentation for more.

### Use an external service for routing rules

You can use an external service for processing your routing by setting the
`rulesType` to `EXTERNAL` and configuring the `rulesExternalConfiguration`.

Trino Gateway then sends all headers, other than those specified in
`excludeHeaders`, as a map in the body of a POST request to the external
service. If `requestAnalyzerConfig.analyzeRequest` is set to `true`,
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

The external service can process the information in any way desired and must
return a result with the following criteria:

* Response status code of OK (200)
* Message in JSON format
* Only one group can be returned
* If errors is not null, then query would route to default routing group adhoc

#### Request headers modification

The external routing service can optionally return an `externalHeaders` map in its response
to add or modify HTTP headers before the request is forwarded.

This enables dynamic customization of request behavior, such as injecting session properties
or setting client tags before the request reaches the Trino cluster.

```json
{
    "routingGroup": "test-group",
    "errors": [
        "Error1",
        "Error2",
        "Error3"
    ],
    "externalHeaders": {
        "x-trino-client-tags": "['etl']",
        "x-trino-session": "query_max_memory=50GB,optimize_metadata_queries=false"
    }
}
```

### Configure routing rules with a file

Rules consist of a name, description, condition, and list
of actions. If the condition of a particular rule evaluates to `true`, its
actions are fired. Rules are stored as a 
[multi-document](https://www.yaml.info/learn/document.html) YAML file. 

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

Three objects are available by default. They are
* `request`, the incoming request as an [HttpServletRequest](https://docs.oracle.com/javaee/6/api/javax/servlet/http/HttpServletRequest.html)
* `state`, a `HashMap<String, Object>` that allows passing arbitrary state from one rule evaluation to the next
* `result`, a `HashMap<String, String>` that is used to return the result of rule evaluation to the engine

In addition to the default objects, rules may optionally utilize
[trinoRequestUser](#trinorequestuser) and
[trinoQueryProperties](#trinoqueryproperties)
, which provide information about the user and query respectively.
You must include an action of the form `result.put(\"routingGroup\", \"foo\")`
to trigger routing of a request that satisfies the condition to the specific
routing group. Without this action, the default adhoc group is used and the
whole routing rule is redundant.

The condition and actions are written in [MVEL](http://mvel.documentnode.com/),
an expression language with Java-like syntax. Classes from `java.util`, data-type 
classes from `java.lang` such as `Integer` and `String`, as well as `java.lang.Math`
and `java.lang.StrictMath` are available in rules. Rules may not use `java.lang.System`
and other classes that allow access the Java runtime and operating system.
In most cases, you can write
conditions and actions in Java syntax and expect it to work. One exception is 
parametrized types. Exclude type parameters, for example to add a `HashSet` to the
`state` variable, use an action such as:
```java
actions:
  - |
    state.put("triggeredRules",new HashSet())
```
This is equivalent to `new HashSet<Object>()`. 

There are some
MVEL-specific operators. For example, instead of doing a null-check before
accessing the `String.contains` method like this:

```yaml
condition: 'request.getHeader("X-Trino-Client-Tags") != null && request.getHeader("X-Trino-Client-Tags").contains("label=foo")'
```

You can use the `contains` operator

```yaml
condition: 'request.getHeader("X-Trino-Client-Tags") contains "label=foo"'
```

If no rules match, then the request is routed to the default `adhoc` routing
group.

### TrinoStatus

The `TrinoStatus` class attempts to track the current state of the configured
Trino clusters. The three possible states of these cluster are updated with
every healthcheck:

- `PENDING`: A Trino cluster shows this state when it is still starting up. It
  is treated as unhealthy by `RoutingManager`, and therefore requests are
  not be routed to these clusters.
- `HEALTHY`: A Trino cluster shows this state when healthchecks report
  the cluster as healthy and ready. `RoutingManager` only routes requests to
  healthy clusters.
- `UNHEALTHY`: A Trino cluster shows this state when healthchecks report the
  cluster as unhealthy. `RoutingManager`  does not route requests to unhealthy
  clusters.

### TrinoRequestUser

The  `TrinoRequestUser` class attempts to extract user information from a
request, in the following order:

1. `X-Trino-User` header.
2. `Authorization: Basic` header.
3. `Authorization: Bearer` header. Requires configuring an OAuth2 User Info URL.
4. `Trino-UI-Token` or `__Secure-Trino-ID-Token` cookie.

Kerberos and Certificate authentication are not currently supported. If the
request contains the `Authorization: Bearer` header, an attempt is made to treat
the token as a JWT and deserialize it. If this is successful, the value of the
claim named in `requestAnalyzerConfig.tokenUserField` is used as the username.
By default, this is the `email` claim. If the token is not a valid JWT, and
`requestAnalyzerConfig.oauthTokenInfoUrl` is configured, then the token is
exchanged with the Info URL. Responses are cached for 10 minutes to avoid
triggering rate limits.

You may call `trinoRequestUser.getUser()` and `trinoRequestUser.getUserInfo()`
in your routing rules. If user information was not successfully extracted,
`trinoRequestUser.getUser()` returns an empty `Optional`.
`trinoRequestUser.getUserInfo()` returns an `Optional<UserInfo>`, with an
[OpenID Connect UserInfo](https://www.javadoc.io/doc/com.nimbusds/oauth2-oidc-sdk/5.34/com/nimbusds/openid/connect/sdk/claims/UserInfo.html)
if a token is successfully exchanged with the `oauthTokenInfoUrl`, and an empty
`Optional` otherwise.

`trinoRequestUser.userExistsAndEquals("usernameToTest")` can be used to check a
username against the extracted user. It returns `false` if a user has not been
extracted.

User extraction is only available if enabled by configuring
`requestAnalyzerConfig.analyzeRequest = True`

### TrinoQueryProperties

The `TrinoQueryProperties` class attempts to parse the body of a request to
determine the SQL statement and other information. Note that only a
syntactic analysis is performed.

If a query references a view, then that view is not expanded, and tables
referenced by the view are not recognized. Views and materialized views are
treated as tables and added to the list of tables in all contexts, including
statements such as `CREATE VIEW`.

A routing rule can call the following methods on the `trinoQueryProperties`
object:

* `String errorMessage()`: the error message, only if there was any error while
  creating the `trinoQueryProperties` object.
* `boolean isNewQuerySubmission()`: boolean flag to indicate if the
  request is a POST to the `v1/statement` query endpoint.
* `String getQueryType()`: the class name of the `Statement`, e.g. `ShowCreate`.
  Note that these are not mapped to the `ResourceGroup` query types. For a full
  list of potential query types, see the classes in
  [STATEMENT_QUERY_TYPES](https://github.com/trinodb/trino/blob/2a882933937427e28ea0a3906ab13a60bcb4faad/core/trino-main/src/main/java/io/trino/util/StatementUtils.java#L170)
* `String getResourceGroupQueryType()`: the Resource Group query type, for
  example `SELECT`, `DATA_DEFINITION`. For a full list see [queryType in
  the Trino documentation](https://trino.io/docs/current/admin/resource-groups.html#selector-rules)
* `String getDefaultCatalog()`: the default catalog, if set. It may or may not
  be referenced in the actual SQL
* `String getDefaultSchema()`: the default schema, if set. It may or may not
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
configuration node.

`analyzeRequest`:

Set to `True` to make `trinoQueryProperties` and `trinoRequestUser` available.

`maxBodySize`:

By default, the max body size is 1,000,000 characters. This can be modified by
configuring `maxBodySize`. If the request body is greater or equal to this
limit, Trino Gateway does not process the query. A buffer of length
`maxBodySize` is allocated per query. Reduce this value if you observe
excessive garbage collection at runtime. `maxBodySize` cannot be set to values
larger than 2**31-1, the maximum size of a Java String.

`isClientsUseV2Format`:

Some commercial extensions to Trino use the V2 Request Structure
[V2 style request structure](https://github.com/trinodb/trino/wiki/Trino-v2-client-protocol#submit-a-query).
Support for V2-style requests can be enabled by setting this property to `true`.
If you use a commercial version of Trino, ask your vendor how to set this
configuration.

`tokenUserField`:

When extracting the user from a JWT token, this field is used as the username.
By default, the `email` claim is used.

`oauthTokenInfoUrl`:

If configured, Trino Gateway attempts to retrieve the user info by exchanging
potential authorization tokens with this URL. Responses are cached for 10
minutes to avoid triggering rate limits.

### Execution of rules

All rules whose conditions are satisfied fire. For example, in the "airflow"
and "airflow special" example rules from the following rule priority section, a
query with source `airflow` and label `special` satisfies both rules. The
`routingGroup` is set to `etl` and then to `etl-special` because of the order in
which the rules of defined. If you swap the order of the rules, then you get
`etl` instead.

You can avoid this ordering issue by writing atomic rules, so any query matches
exactly one rule. For example you can change the first rule to the following:

```yaml
---
name: "airflow"
description: "if query from airflow, route to etl group"
condition: 'request.getHeader("X-Trino-Source") == "airflow" && request.getHeader("X-Trino-Client-Tags") == null'
actions:
  - 'result.put("routingGroup", "etl")'
---
```

This can difficult to maintain with more rules. To have better control over the
execution of rules, we can use rule priorities. Overall,
priorities and other constructs that MVEL support allows
you to express your routing logic.

#### Rule priority

You can assign an integer value `priority` to a rule. The lower this integer is,
the earlier it fires. If the priority is not specified, the priority defaults to
`INT_MAX`. Following is an example with priorities:

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

Note that both rules still fire. The difference is that you are guaranteed
that the first rule (priority 0) is fired before the second rule (priority 1).
Thus `routingGroup` is set to `etl` and then to `etl-special`, so the
`routingGroup` is always `etl-special` in the end.

More specific rules must be set to a higher priority so they are evaluated last
to set a `routingGroup`.

##### Passing State

The `state` object may be used to pass information from one rule evaluation to
the next. This allows an author to avoid duplicating logic in multiple rules.
Priority should be used to ensure that `state` is updated before being used 
in downstream rules. For example, the atomic rules may be re-implemented as

```yaml
---
name: "initialize state"
description: "Add a set to the state map to track rules that have evaluated to true"
priority: 0
condition: "true"
actions:
  - |
    state.put("triggeredRules",new HashSet())
  # MVEL does not support type parameters! HashSet<String>() would result in an error.
---
name: "airflow"
description: "if query from airflow, route to etl group"
priority: 1
condition: |
  request.getHeader("X-Trino-Source") == "airflow"
actions:
  - |
    result.put("routingGroup", "etl")
  - |
    state.get("triggeredRules").add("airflow")
---
name: "airflow special"
description: "if query from airflow with special label, route to etl-special group"
priority: 2
condition: |
  state.get("triggeredRules").contains("airflow") && request.getHeader("X-Trino-Client-Tags") contains "label=special"
actions:
  - |
    result.put("routingGroup", "etl-special")

```

##### If statements (MVEL Flow Control)

You can use MVEL support for `if` statements and other flow control. The following logic
in pseudocode:

```text
if source == "airflow":
  if clientTags["label"] == "foo":
    return "etl-foo"
  else if clientTags["label"] = "bar":
    return "etl-bar"
  else
    return "etl"
```

This logic Can be implemented with the following rules:

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
