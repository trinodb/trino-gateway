**Trino Gateway documentation**

<table>
  <tr>
    <td><b><a href="installation.md">Installation</a></b></td>
    <td><a href="design.md">Design</a></td>
    <td><a href="development.md">Development</a></td>
    <td><a href="security.md">Security</a></td>
    <td><a href="operation.md">Operation</a></td>
    <td><a href="gateway-api.md">Gateway API</a></td>
    <td><a href="resource-groups-api.md">Resource groups API</a></td>
    <td><a href="routing-rules.md">Routing rules</a></td>
    <td><a href="references.md">References</a></td>
    <td><a href="release-notes.md">Release notes</a></td>
  </tr>
</table>

# Installation

Trino Gateway is distributed as an executable JAR file. The [release
notes](release-notes.md) contain links to download specific versions.
Alternatively, you can follow the [development instructions](development.md) to
build the JAR file.

## Requirements

Consider the following requirements for your Trino Gateway installation.

### Java

Trino Gateway requires a Java 17 runtime. Older or newer versions of Java are
not tested and not supported.

### Operating system

No specific operating system is required. All testing and development is
performed with Linux and MacOS.

### Backend database

Trino Gateway requires a MySQL or PostgreSQL database.

Use the following scripts to initialize the database:

* [gateway-ha-persistence.sql](../gateway-ha/src/main/resources/gateway-ha-persistence.sql) for MySQL
* [gateway-ha-persistence-postgres.sql](../gateway-ha/src/main/resources/gateway-ha-persistence-postgres.sql) for PostgreSQL

The files are also included in the JAR file.

### Trino clusters

The proxied Trino clusters behind the Trino Gateway must support the Trino JDBC
driver and the Trino REST API for cluster and node health information.
Typically, this means that Trino versions 354 and higher should work, however
newer Trino versions are strongly recommended.

## Configuration

After downloading or building the JAR, rename it to `gateway-ha.jar`.

Copy the example config file
[gateway-ha-config.yml](../gateway-ha/gateway-ha-config.yml) and update the
configuration as needed.

Each component of the Trino Gateway has a corresponding node in the
configuration YAML file.

### Configure routing rules



### Proxying additional paths

By default, Trino Gateway only proxies requests to paths starting with
`/v1/statement`, `/v1/query`, `/ui`, `/v1/info`, `/v1/node`, `/ui/api/stats` and
`/oauth`.

If you want to proxy additional paths, you can add them by adding the
`extraWhitelistPaths` node to your configuration YAML file:

```yaml
extraWhitelistPaths:
  - "/ui/insights"
  - "/api/v1/biac"
  - "/api/v1/dataProduct"
  - "/api/v1/dataproduct"
  - "/ext/faster"
```

## Running Trino Gateway

Start Trino Gateway with the following java command:

```shell
java -XX:MinRAMPercentage=50 -XX:MaxRAMPercentage=80 \
    --add-opens=java.base/java.lang=ALL-UNNAMED \
    --add-opens=java.base/java.net=ALL-UNNAMED \
    -jar gateway-ha.jar server gateway-config.yml
```


