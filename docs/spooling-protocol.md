# Spooling protocol

Support for [Spooling protocol](https://trino.io/docs/current/client/client-protocol.html#spooling-protocol)
retrieval modes is currently limited.

## Supported retrieval modes

The following [retrieval mode](https://trino.io/docs/current/admin/properties-client-protocol.html#protocol-spooling-retrieval-mode)
is currently supported:

- `STORAGE` (partial support)

With `STORAGE`, the client downloads spooled result segments directly from the
results storage system, and query results are typically returned to the client.
Clients must have direct network access to the configured spooling storage
location ([fs.location](https://trino.io/docs/current/admin/properties-client-protocol.html#fs-location)).

### Limitation in `STORAGE` mode

Requests to `/v1/spooled/ack/{id}` are not currently forwarded to Trino
coordinators and return `404`. This means segment acknowledgement and cleanup
are not fully handled through gateway. As a consequence, result files may
remain in storage longer than expected and are not guaranteed to be removed
after consumption.

## Unsupported retrieval modes

The following retrieval modes are currently not supported:

- `COORDINATOR_STORAGE_REDIRECT`
- `COORDINATOR_PROXY`
- `WORKER_PROXY`

When these modes are used through Trino Gateway, clients can observe partial
results only. The corresponding Trino coordinator reports the query as
`USER CANCELED — USER_CANCELED`.

Depending on the client implementation, the failure appears in a similar way:
client trace logs typically show a `404` on `/v1/spooled/...`, after which the
client aborts further result retrieval for that query. Since the spooling
protocol includes a small inline result chunk in the initial statement
response, some rows can still appear before subsequent spooled fetch requests
fail.
