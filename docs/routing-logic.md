# Routing Logic

## Overview

Trino Gateway checks incoming requests to see if they're related to previous 
ones it handled. If they are, then Trino Gateway sends them to the same 
Trino cluster that dealt with the earlier requests.

If it is a new request, the Trino Gateway refers to [Routing rules](routing-rules.md) 
to decide which group of clusters, called a 'Routing Group,' should handle it. 
It then picks a cluster from that Routing Group to handle the request using 
either an adaptive or round-robin strategy.

![Request Routing Flow](assets/gateway-routing-flow.svg)

## Sticky routing

A request related to an ongoing process, or to state maintained on a single 
Trino cluster, must be routed to that cluster for proper handling. Two 
mechanisms for identifying related requests are currently implemented. By default,
only routing based on query identifier is enabled.

### Routing based on query identifier (default)

When a query is initiated through the Trino Gateway, the query id will be 
extracted from the response and mapped to the cluster that provided the 
response. Any subsequent request containing that query id will be forwarded 
to that cluster. For example, to retrieve query results, the Trino client 
polls a URI of the form 
`v1/statement/executing/queryid/nonce/counter`. The Trino Gateway will extract
the queryid from this URI.

### Routing based on cookies

OAuth2 authentication requires that the same cluster is used for each step of 
the handshake. When `gatewayCookieConfiguration.enabled` is set to true, a cookie 
will be added to requests made to paths beginning with `/oauth2` unless they already have 
a cookie present, which is used to route further `/oauth2/*` requests to the correct cluster. 
Cookies are not added to requests to `v1/*` and other Trino endpoints.

Trino Gateway signs its cookies to ensure that they are not tampered with. You 
must set a `cookieSigningSecret` string in your configuration
```yaml
gatewayCookieConfiguration:
    enabled: true
    cookieSigningSecret: "ahighentropystring"
```
when making use of this feature. If you load balance request across multiple Trino Gateway
instances, ensure each instance has the same `cookieSigningSecret`.

The Trino Gateway handles standard Trino OAuth2 handshakes with no additional 
configuration. If you are using a customized or commercial Trino distribution, then
the paths used to define the OAuth handshake may be modified.

`routingPaths`: If the request URI starts with a path in this list, then

* If no cookie is present, add a routing cookie
* If a cookie is present, route the request to the cluster defined by that cookie

`deletePaths`: If the request URI starts with a path in this list,
return a response that instructs the client to delete the cookie.

Additionally, the `lifetime` property sets the duration for which a cookie remains in 
effect after creation. Ensure that it is greater than
the time required to complete the handshake. Default `lifetime` is 10 minutes.

These properties are defined under the `oauth2GatewayCookieConfiguration` node: 

```yaml
oauth2GatewayCookieConfiguration:
  routingPaths:
    - "/oauth2"
    - "/custom/oauth2/callback"
    - "/alternative/oauth2/initiate"
  deletePaths:
    - "/custom/logout"
  lifetime: "5m"
```

### Routing based on OAuth2 token-exchange pinning (opt-in)

Trino's OAuth2 token-exchange handshake for the CLI/driver spans two HTTP
clients that only share an internal identifier (`authId`, and its hash):

* the driver/CLI polls `GET /oauth2/token/{authId}` until the login completes
* the browser is redirected to `GET /oauth2/token/initiate/{authIdHash}`, and
  then returns from the identity provider to `GET /oauth2/callback` (which
  carries the `authIdHash` inside its signed `state` parameter)

Only the Trino coordinator that issued the original `401` challenge holds the
in-memory state for that handshake. In a deployment with multiple
coordinators, Trino Gateway's normal routing may send the poll loop, the
initiate redirect, or the callback to a different coordinator than the one
that started the handshake, causing the login to stall. The cookie-based
routing above cannot solve this on its own: the CLI/driver poll loop does not
carry cookies.

When `routing.oauth2RoutingEnabled` is set to true, Trino Gateway records the
`authId`/`authIdHash` advertised in the `401` challenge together with the
coordinator that issued it, and pins every later request of that handshake —
the poll loop, the initiate redirect, and the callback — back to the same
coordinator:

```yaml
routing:
  oauth2RoutingEnabled: true
```

Pins are stored in the gateway's database so they are visible across every
Trino Gateway instance, and are cleaned up automatically; see
`dataStore.oauth2RoutingHoursRetention` (default 1 hour) to change how long a
pin is kept. If the pinned coordinator becomes unavailable before the
handshake completes, Trino Gateway drops the pin and asks the client to
re-authenticate, since the handshake cannot be resumed on another coordinator.

This feature is off by default.

## Routing URLs

Each Trino cluster configured with the Trino Gateway includes both a `proxyTo` 
URL and an `externalURL`. The `proxyTo` URL is used internally by the Trino 
Gateway to route requests based on routing rules and query identifiers, whereas 
the `externalURL` serves as a UI-accessible or publicly reachable address for 
the Trino cluster, and is commonly used to access [Trino web UI](https://trino.io/docs/current/admin/web-interface.html)  

For example, in a Kubernetes environment, the `proxyTo` URL might be 
`trino-backend-service.trino-namespace.svc.cluster.local:8083` for communication 
between the Trino Gateway and Trino clusters, and the external URL for the same 
backend cluster might be `trino.domain.com`.
