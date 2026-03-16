# Backend tags

Backend tags allow you to attach arbitrary metadata to a gateway backend. Today,
the only mechanism for influencing routing decisions is the `routingGroup` field,
which maps a query to a named cluster group. There is no way to store additional
metadata on a backend that routing logic could inspect. Tags provide that
foundation — a freeform list of strings on each backend that can be read by
custom routing rules, scripts, or future gateway enhancements to enable more
sophisticated routing behavior.

Tags are stored as a list of strings on the backend and are returned in all
backend API responses. A common convention is `key:value` pairs (e.g. `env:prod`,
`team:data-eng`), but any string is accepted.

## Setting tags

Tags can be set when creating or updating a backend via the
[Gateway API](gateway-api.md):

```shell
curl -X POST http://localhost:8080/gateway/backend/modify/add \
 -H "Content-Type: application/json" \
 -d '{  "name": "trino-3",
        "proxyTo": "http://localhost:8083",
        "active": true,
        "routingGroup": "adhoc",
        "tags": ["env:prod", "team:data-eng", "size:XL"]
    }'
```

Tags are included in all backend list responses:

```json
[
    {
        "name": "trino-1",
        "proxyTo": "http://localhost:8081",
        "active": true,
        "routingGroup": "adhoc",
        "externalUrl": "http://localhost:8081",
        "tags": ["env:prod", "size:S"]
    },
    {
        "name": "trino-2",
        "proxyTo": "http://localhost:8082",
        "active": true,
        "routingGroup": "adhoc",
        "externalUrl": "http://localhost:8082",
        "tags": ["env:test", "size:XL"]
    }
]
```

## Use cases

Tags are a building block. The following examples illustrate the kinds of
features that can be built on top of them. **These capabilities do not exist
today** — they are examples of what custom routing rules or future gateway
enhancements could implement using tags as the metadata source.

### Sophisticated routing

- **Environment routing** — Tag backends with `env:prod` or `env:test` and route
  queries to the appropriate environment based on the query source or user.
- **Team-based routing** — Tag backends with `team:bi-tools` or `team:data-eng`
  and steer each team's queries to their dedicated cluster.
- **Size-based routing** — Tag backends with `size:S`, `size:M`, or `size:XL`
  and route large, resource-heavy queries to large clusters. Large queries can
  hog resources for a long time, so routing them to appropriately sized clusters
  reduces wait times for other queries.

### Cluster upgrades and canary deployments

- **Version tags** — Tag backends with `ver:476` to identify which Trino version
  a cluster is running. Routing rules can use this to drop deprecated session
  properties before forwarding queries to older or newer clusters.
- **Canary deployments** — Tag a backend with `route_percent:10` to signal that
  only 10% of traffic should be sent to that cluster, enabling gradual rollouts
  before promoting a new version to full traffic.

### Operational metadata

- **Region awareness** — Tag backends with the region they are deployed in (e.g.
  `region:us-east-1`) to support geo-aware routing or failover logic.
- **Ownership** — Tag backends with the owning team (e.g. `owner:platform-eng`)
  to make it easier to identify cluster ownership in dashboards or alerting.