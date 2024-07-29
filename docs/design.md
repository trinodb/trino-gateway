# Design

There are two types of requests: one is a request to Trino Gateway, and the
other is a request that needs to be forwarded to Trino.

## Request forwarding

Trino Gateway forwards some pre-defined URIs automatically to Trino. You can
configure additional URIs to forward with the `extraWhitelistPaths`
configuration.

In order to support additional URIs that are only known at runtime, the
`RouterPreMatchContainerRequestFilter` is used to process every request before
the actual resource matching occurs. If the requests URI matches, the request
is forwarded to `RouteToBackendResource`.

Flow of request forwarding:

1. Determine to which Trino cluster a query should be routed to.
2. Prepare a request to send to Trino by adding `Via` headers and `X-Forwarded`
   headers. Most headers are forwarded to Trino unmodified.
3. Some request URI require special handling. For example, a
   request which submit a new query, Trino Gateway retrieves the queryId from the
   response from Trino. Some requests to the web UI require setting a session
   cookie to ensure OIDC works. These are done by chaining asynchronous
   operations using `Future`.
4. The execution of requests to Trino and the response to the client are handled
   by `airlift.jaxrs.AsyncResponseHandler`.
