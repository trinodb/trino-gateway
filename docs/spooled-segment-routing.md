# Spooled segment routing

## Problem

With the Trino [spooling protocol](https://trino.io/docs/current/client/client-protocol.html)
in `COORDINATOR_PROXY` retrieval mode, clients collect results with
`GET /v1/spooled/download/<token>` and acknowledge them with
`GET /v1/spooled/ack/<token>`. As produced by Trino, these URIs carry no user,
no client tags and no query id, so neither the
[sticky routing](routing-logic.md#sticky-routing) mechanisms nor the
[routing rules](routing-rules.md) can identify the cluster that produced the
segment.

With two or more spooling clusters behind one Trino Gateway, segment requests
are routed to a backend of the default routing group. If the query ran on
another cluster, the token cannot be resolved by the receiving coordinator and
the client fails with *"Error fetching results"*. When
`protocol.spooling.inlining.enabled=false` every result is spooled, so even
single-row queries fail.

## Design

Trino Gateway makes the spooled segment URIs self-contained. It knows which
backend served a query at the moment it proxies the query results response, so
it embeds that knowledge into the URIs the client calls back with.

### Response rewrite

Every successful response on a statement path is checked for spooled segments.
Each spooled segment's `uri` and `ackUri` gets two query parameters appended:

```
/v1/spooled/download/<token>?queryId=<query id>&spooledBackend=<backend name>
```

The backend name is the name the cluster is registered with in Trino Gateway.
The backend URL is never exposed to clients. Trino coordinators ignore unknown
query parameters, so the rewritten URIs remain valid against the backend.

Responses that are not `200 OK`, contain no spooled segments, or fail to parse
are proxied unmodified, and a warning is logged:
`Failed to rewrite spooled segment URIs, response is proxied unmodified`.

### Request routing

For requests under `/v1/spooled`, the backend is resolved in this order:

1. The `spooledBackend` parameter. The name is looked up among all configured
   backends, including deactivated ones. An unknown name is ignored, so the
   parameter can only select a cluster Trino Gateway already proxies to, and
   cannot be used to reach arbitrary hosts.
2. The `queryId` parameter, using the existing
   [query identifier routing](routing-logic.md#routing-based-on-query-identifier-default).
3. The routing rules, as for any other request.

## Why the backend is embedded rather than looked up

Routing based on the query id depends on state with a limited lifetime:

| State                                  | Lifetime                                              |
|----------------------------------------|-------------------------------------------------------|
| In-memory query id to backend cache    | Per Trino Gateway instance, until restart or eviction |
| Query history database                 | Until the periodic history cleanup                    |
| Probing backends with `/v1/query/{id}` | Until the query is purged from the coordinator        |

A client fetching a spooled segment after all three have expired would be
routed by the routing rules, which reintroduces the problem. With the backend
embedded in the URI, a segment can be fetched as long as it exists in spooling
storage. The only remaining bound is the segment time-to-live configured in
Trino.

## Cluster changes

| Scenario                                                              | Outcome                                                                                                                         |
|-----------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------|
| Backend `proxyTo` URL changed, same name                              | Segment requests are routed to the new URL.                                                                                     |
| Backend renamed                                                       | The old name is unknown, and routing falls back to the query id.                                                                |
| Backend deactivated                                                   | Segment requests are still routed to it, so in-flight result collection completes.                                             |
| Backend down but still configured                                     | Segment requests fail. The segments are only reachable through that cluster, so routing them elsewhere cannot succeed.          |
| Backend deleted                                                       | The name is unknown, and routing falls back to the query id.                                                                    |
| Cluster restarted with a new spooling `shared-secret-key` or location | The request reaches the cluster, but the coordinator rejects the token. This cannot be addressed by Trino Gateway.              |

A spooled segment token is bound to the spooling secret and storage location of
the cluster that produced it. Once that cluster is gone, no routing decision can
recover the segment, and the client must rerun the query. When replacing a
cluster, deactivate the old backend and keep it configured until in-flight
result collection has completed, instead of deleting it immediately.
