**Trino Gateway documentation**

<table>
  <tr>
    <td>
      <img src="./assets/logos/trino-gateway-v.png"/>
    </td>
    <td>
      <ul>
        <li><a href="quickstart.md">Quickstart</a></li>
        <li><a href="installation.md">Installation</a></li>
        <li><a href="security.md">Security</a></li>
        <li><a href="operation.md">Operation</a></li>
      </ul>
    </td>
    <td>
      <ul>
        <li><a href="gateway-api.md">Gateway API</a></li>
        <li><a href="resource-groups-api.md">Resource groups API</a></li>
        <li><a href="routing-rules.md">Routing rules</a></li>
      </ul>
    </td>
    <td>
      <ul>
        <li><a href="design.md">Design</a></li>
        <li><a href="development.md">Development</a></li>
        <li><a href="release-notes.md">Release notes</a></li>
        <li><a href="references.md">References</a></li>
      </ul>
    </td>
  </tr>
</table>

# Installation

Trino Gateway is distributed as an executable JAR file. The [release
notes](release-notes.md) contain links to download specific versions.
Alternatively, you can look at the [development instructions](development.md) to
build the JAR file or use the TrinoGatewayRunner for local testing.
The [quickstart guide](quickstart.md) contains instructions for running the
application locally. 

Following are instructions for installing Trino Gateway for production
environments.

## Requirements

Consider the following requirements for your Trino Gateway installation.

### Java

Trino Gateway requires a Java 17 runtime. Older versions of Java can not be
used. Newer versions might work but are not tested.

Verify the Java version on your system with `java -version`.

### Operating system

No specific operating system is required. All testing and development is
performed with Linux and MacOS.

### Processor architecture

No specific processor architecture is required, as long as a suitable Java
distribution is installed.  

### Backend database

Trino Gateway requires a MySQL or PostgreSQL database.

Use the following scripts to initialize the database:

* [gateway-ha-persistence-mysql.sql](../gateway-ha/src/main/resources/gateway-ha-persistence-mysql.sql) for MySQL
* [gateway-ha-persistence-postgres.sql](../gateway-ha/src/main/resources/gateway-ha-persistence-postgres.sql) for PostgreSQL

The files are also included in the JAR file.

### Trino clusters

The proxied Trino clusters behind the Trino Gateway must support the Trino JDBC
driver and the Trino REST API for cluster and node health information.
Typically, this means that Trino versions 354 and higher should work, however
newer Trino versions are strongly recommended.

Trino-derived projects and platforms may work if the Trino JDBC driver and the
REST API are supported. For example, Starburst Galaxy and Starburst Enterprise
are known to work. Trino deployments with the Helm chart and other means on
various cloud platforms, such as Amazon EKS also work. However Amazon Athena
does not work since it uses alternative, custom protocols and lacks the concept
of individual clusters.

## Configuration

After downloading or building the JAR, rename it to `gateway-ha.jar`,
and place it in a directory with read and write access such as `/opt/trinogateway`.

Copy the example config file
[gateway-ha-config.yml](../gateway-ha/gateway-ha-config.yml)  into the same
directory, and update the configuration as needed.

Each component of the Trino Gateway has a corresponding node in the
configuration YAML file.

### Configure routing rules

Find more information in the [routing rules documentation](routing-rules.md).

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

Start Trino Gateway with the following java command in the directory of the
JAR and YAML files:

```shell
java -XX:MinRAMPercentage=50 -XX:MaxRAMPercentage=80 \
    --add-opens=java.base/java.lang=ALL-UNNAMED \
    --add-opens=java.base/java.net=ALL-UNNAMED \
    -jar gateway-ha.jar server gateway-config.yml
```
