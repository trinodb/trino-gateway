# Gateway API

The REST API for Trino Gateway can be used to update routing configuration for
the Trino clusters. Note that the API calls do not perform actions on the
clusters themselves.

The example commands are for a Trino Gateway server running at
`http://localhost:8080`.

If there are duplicate `proxyTo` URLs in the configuration, the `Name` in the
**Query History** page of the UI might not show correctly.

## Add or update a Trino cluster

```shell
curl -X POST http://localhost:8080/entity?entityType=GATEWAY_BACKEND \
 -d '{  "name": "trino-3",
        "proxyTo": "http://localhost:8083",
        "active": true,
        "routingGroup": "adhoc"
    }'
```

If the Trino cluster URL is different from the `proxyTo` URL, for example if
they are internal and external hostnames used, you can use the optional
`externalUrl` field to override the link in the **Active Backends** page.

```shell
curl -X POST http://localhost:8080/entity?entityType=GATEWAY_BACKEND \
 -d '{  "name": "trino-3",
        "proxyTo": "http://localhost:8083",
        "active": true,
        "routingGroup": "adhoc",
        "externalUrl": "http://localhost:8084"
    }'
```

## List all Trino clusters

```shell
curl -X GET http://localhost:8080/entity/GATEWAY_BACKEND
```

Returns a JSON array of Trino cluster:

```json
[
    {
        "name": "trino-1",
        "proxyTo": "http://localhost:8081",
        "active": true,
        "routingGroup": "adhoc",
        "externalUrl": "http://localhost:8081"
    },
    {
        "name": "trino-2",
        "proxyTo": "http://localhost:8082",
        "active": true,
        "routingGroup": "adhoc",
        "externalUrl": "http://localhost:8082"
    },
    {
        "name": "trino-3",
        "proxyTo": "http://localhost:8083",
        "active": true,
        "routingGroup": "adhoc",
        "externalUrl": "http://localhost:8084"
    }
]
```

## Delete a Trino cluster

```shell
curl -X POST -d "trino3" http://localhost:8080/gateway/backend/modify/delete
```

## Deactivate a Trino cluster

```shell
curl -X POST http://localhost:8080/gateway/backend/deactivate/trino-2
```

## List all active Trino clusters

```shell
curl -X GET http://localhost:8080/gateway/backend/active
```

Returns a JSON array of active Trino clusters:

```json
[
    {
        "name": "trino-1",
        "proxyTo": "http://localhost:8081",
        "active": true,
        "routingGroup": "adhoc",
        "externalUrl": "http://localhost:8081"
    }
]
```

## Activate a Trino cluster

```shell
curl -X POST http://localhost:8080/gateway/backend/activate/trino-2
```

## Update routing rules

The API can be used to programmatically update the routing rules. Rule are
updated based on the rule name. Storage of the rules must use a writeable file
and the configuration 'rulesType: FILE'.

For this feature to work with multiple replicas of the Trino Gateway, you must
provide a shared storage that supports file locking for the routing rules file.
If multiple replicas are used with local storage, then rules get out of
sync when updated.

```shell
curl -X POST http://localhost:8080/webapp/updateRoutingRules \
 -H 'Content-Type: application/json' \
 -d '{  "name": "trino-rule",
        "description": "updated rule description",
        "priority": 0,
        "actions": ["updated action"],
        "condition": "updated condition"
    }'
```

