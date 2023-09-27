**Trino Gateway documentation**

<table>
  <tr>
    <td><a href="design.md">Design</a></td>
    <td><a href="development.md">Development</a></b></td>
    <td><a href="security.md">Security</a></td>
    <td><a href="operation.md">Operation</a></td>
    <td><a href="gateway-api.md">Gateway API</a></td>
    <td><a href="resource-groups-api.md">Resource groups API</a></td>
    <td><a href="routing-rules.md">Routing rules</a></td>
    <td><a href="references.md">References</a></td>
    <td><b><a href="release-notes.md">Release notes</a></b></td>
  </tr>
</table>

# Release notes

## Trino Gateway 3 (26 Sep 2023)

* [gateway-ha-3-jar-with-dependencies.jar](https://repo1.maven.org/maven2/io/trino/gateway/gateway-ha/3/gateway-ha-3-jar-with-dependencies.jar)

The first release of Trino Gateway is based on the [Presto
Gateway](https://github.com/lyft/presto-gateway/) 1.9.5 codebase
[#4](#4), with these additions:

* Add authentication and authorization with LDAP, OIDC and user list from config
  file. (https://github.com/trinodb/trino-gateway/pull/9)
* Add support for user, admin and API roles. (https://github.com/trinodb/trino-gateway/pull/9)
* Add healthcheck for Trino backends using JDBC. (https://github.com/trinodb/trino-gateway/pull/9)
* Add TCP check for routing. (https://github.com/trinodb/trino-gateway/pull/9)
* Add logic to route requests only to healthy backends. (https://github.com/trinodb/trino-gateway/pull/13)
* Add PostgreSQL support for backend database. (https://github.com/trinodb/trino-gateway/pull/13)
* Allow routing of `/v1/node` endpoint URL. (https://github.com/trinodb/trino-gateway/pull/27)
* Filter logs for sensitive information. (https://github.com/trinodb/trino-gateway/pull/9)
* Require Java 17 for build and runtime. (https://github.com/trinodb/trino-gateway/pull/16)
* Deactivate clusters with zero workers. (https://github.com/trinodb/trino-gateway/pull/13)
* Remove concurrency issue from repeated rules file loading. (https://github.com/trinodb/trino-gateway/pull/9)
