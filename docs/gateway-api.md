# Gateway API

## Add or update a backend

```$xslt
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

```$xslt
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
```$xslt
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

```$xslt
curl -X POST -d "trino3" http://localhost:8080/gateway/backend/modify/delete
```

## Deactivate a backend

```$xslt
curl -X POST http://localhost:8080/gateway/backend/deactivate/trino-2
```

## Get all active backends

`curl -X GET http://localhost:8080/gateway/backend/active`

```
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

`curl -X POST http://localhost:8080/gateway/backend/activate/trino-2`

