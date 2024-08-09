# Gateway API

## Add or update a backend

```shell
curl -X POST http://localhost:8080/entity?entityType=GATEWAY_BACKEND \
 -d '{  "name": "trino-3",
        "proxyTo": "http://localhost:8083",
        "active": true,
        "routingGroup": "adhoc"
    }'
```

If the backend URL is different from the `proxyTo` URL (for example if they are
internal vs. external hostnames). You can use the optional `externalUrl` field
to override the link in the Active Backends page.

```shell
curl -X POST http://localhost:8080/entity?entityType=GATEWAY_BACKEND \
 -d '{  "name": "trino-3",
        "proxyTo": "http://localhost:8083",
        "active": true,
        "routingGroup": "adhoc",
        "externalUrl": "http://localhost:8084"
    }'
```

## Get all backends

`curl -X GET http://localhost:8080/entity/GATEWAY_BACKEND`
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

## Delete a backend

```shell
curl -X POST -d "trino3" http://localhost:8080/gateway/backend/modify/delete
```

## Deactivate a backend

```shell
curl -X POST http://localhost:8080/gateway/backend/deactivate/trino-2
```

## Get all active backends

```shell
curl -X GET http://localhost:8080/gateway/backend/active
```

Will return a JSON array of active Trino cluster backends:
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

## Activate a backend

```shell
curl -X POST http://localhost:8080/gateway/backend/activate/trino-2
```

## Update Routing Rules

This API can be used to programmatically update the Routing Rules.
Rule will be updated based on the rule name.

For this feature to work with multiple replicas of the Trino Gateway, you will need to provide a shared storage that supports file locking for the routing rules file. If multiple replicas are used with local storage, then rules will get out of sync when updated.

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
### Disable Routing Rules UI

You can set the `disablePages` config to disable pages on the UI.

The following pages are available:
- `dashboard`
- `cluster`
- `resource-group`
- `selector`
- `history`
- `routing-rules`

```yaml
uiConfiguration:
  disablePages:
    - 'routing-rules'
```
