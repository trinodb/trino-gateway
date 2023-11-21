**Trino Gateway documentation**

<table>
  <tr>
    <td><a href="installation.md">Installation</a></td>
    <td><a href="design.md">Design</a></td>
    <td><a href="development.md">Development</a></td>
    <td><a href="security.md">Security</a></td>
    <td><a href="quickstart.md">Quickstart</a></td>
    <td><b><a href="operation.md">Operation</a></b></td>
    <td><a href="gateway-api.md">Gateway API</a></td>
    <td><a href="resource-groups-api.md">Resource groups API</a></td>
    <td><a href="routing-rules.md">Routing rules</a></td>
    <td><a href="references.md">References</a></td>
    <td><a href="release-notes.md">Release notes</a></td>
  </tr>
</table>

# Operation

The following aspects apply to managing Trino Gateway and the connected Trino
clusters.

## Query History UI - check query plans etc.

Trino Gateway records history of recent queries and displays links to check query
details page in respective trino cluster.
![trino.gateway.io](/docs/assets/trinogateway_query_history.png)

## Admin UI - add and modify backend information

The admin page is used to configure the gateway to multiple backends.
Existing backend information can also be modified using the same.

![trino.gateway.io/entity](/docs/assets/trinogateway_ha_admin.png)


## Graceful shutdown

Trino Gateway supports graceful shutdown of trino clusters. Even when a cluster
is deactivated, any submitted query states can still be retrieved based on the
Query ID.

To graceful shutdown a trino cluster without query losses, the steps are:

1. Set the backend to deactivate state, this prevents any new incoming queries
   from getting assigned to the backend.
2. Poll the trino backend coorinator URL until the queued query count and the
   running query count both hit 0.
3. Terminate the trino Coordinator & Worker Java process.

To gracefully shutdown a single worker process, see
[this](https://trino.io/docs/current/admin/graceful-shutdown.html) for the
operations.

