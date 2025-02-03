# Installation

Trino Gateway is distributed as an executable JAR file. The [release
notes](release-notes.md) contain links to download specific versions.

Every Trino Gateway release includes a [Docker container](docker.md) and a 
[Helm chart](installation.md#helm) as alternative deployment methods.

Follow the [development instructions](development.md) to
build the JAR file and the Docker image instructions  or use the 
`TrinoGatewayRunner` class for local testing.
The [quickstart guide](quickstart.md) contains instructions for running the
application locally.

Following are instructions for installing Trino Gateway for production
environments.

## Requirements

Consider the following requirements for your Trino Gateway installation.

### Java

Trino Gateway requires a Java 23 runtime. Older versions of Java can not be
used. Newer versions might work but are not tested.

Verify the Java version on your system with `java -version`.

### Operating system

No specific operating system is required. All testing and development is
performed with Linux and MacOS.

### Processor architecture

No specific processor architecture is required, as long as a suitable Java
distribution is installed.  

### Backend database

Trino Gateway requires a MySQL, PostgreSQL, or Oracle database. Database
initialization is performed automatically when the Trino Gateway process
starts. Migrations are performed using `Flyway`.

The migration files can viewed in the `gateway-ha/src/main/resources/` folder.
Each database type supported has its own sub-folder.

The files are also included in the JAR file.

If you do not want migrations to be performed automatically on startup, then
you can set `runMigrationsEnabled` to `false` in the data store configuration.
For example:

```yaml
dataStore:
  jdbcUrl: jdbc:postgresql://postgres:5432/trino_gateway_db
  user: USER
  password: PASSWORD
  driver: org.postgresql.Driver
  queryHistoryHoursRetention: 24
  runMigrationsEnabled: false
```

`Flyway` uses a transactional lock in databases that support it such as 
[PostgreSQL](https://documentation.red-gate.com/fd/postgresql-database-235241807.html#).
In the scenario where multiple Trino Gateway instances are running and sharing
the same backend database, the first Trino Gateway instance to start will get
the lock and run the database migrations with `Flyway`. Other Trino Gateway
instances might fail during startup while migrations are running but once migrations
are completed they will start as expected.

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

### Trino configuration

From a users perspective Trino Gateway acts as a transparent proxy for one 
or more Trino clusters. The following Trino configuration tips should be 
taken into account for all clusters behind the Trino Gateway.

If all client and server communication is routed through Trino Gateway, 
then process forwarded HTTP headers must be enabled:

```properties
http-server.process-forwarded=true
```

Without this setting, first requests go from the user to Trino Gateway and then
to Trino correctly. However, the URL for subsequent next URIs for more results
in a query provided by Trino is then using the local URL of the Trino cluster,
and not the URL of the Trino Gateway. This circumvents the Trino Gateway for all
these requests. In scenarios, where the local URL of the Trino cluster is private 
to the Trino cluster on the network level, these following calls do not work
at all for users.

This setting is also required for Trino to authenticate in the case TLS is 
terminated at the Trino Gateway. Normally it refuses to authenticate plain HTTP 
requests, but if `http-server.process-forwarded=true` it authenticates over 
HTTP if the request includes `X-Forwarded-Proto: HTTPS`.

To prevent Trino Gateway from sending `X-Forwarded-*` headers, add the following configuration:

```yaml
routing:
  addXForwardedHeaders: false
```

Find more information in [the related Trino documentation](https://trino.io/docs/current/security/tls.html#use-a-load-balancer-to-terminate-tls-https).

## Configuration

After downloading or building the JAR, rename it to `gateway-ha.jar`,
and place it in a directory with read and write access such as `/opt/trinogateway`.

Copy the example config file `gateway-ha-config.yml` from the `gateway-ha/`
directory into the same directory, and update the configuration as needed.

Each component of the Trino Gateway has a corresponding node in the
configuration YAML file.

### Secrets in configuration file

Environment variables can be used as values in the configuration file.
You can manually set an environment variable on the command line.

```shell
export DB_PASSWORD=my-super-secret-pwd
```

To use this variable in the configuration file, you reference it with the 
syntax `${ENV:VARIABLE}`. For example:

```yaml
dataStore:
  jdbcUrl: jdbc:postgresql://localhost:5432/gateway
  user: postgres
  password: ${ENV:DB_PASSWORD}
```

### Configure routing rules

Find more information in the [routing rules documentation](routing-rules.md).

### Configure logging <a name="logging">

To configure the logging level for various classes, specify the path to the 
`log.properties` file by setting `log.levels-file` in `serverConfig`.

For additional configurations, use the `log.*` properties from the 
[Trino logging properties documentation](https://trino.io/docs/current/admin/properties-logging.html) and specify
the properties in `serverConfig`.

### Proxying additional paths

By default, Trino Gateway only proxies requests to paths starting with
`/v1/statement`, `/v1/query`, `/ui`, `/v1/info`, `/v1/node`, `/ui/api/stats` and
`/oauth`.

If you want to proxy additional paths, you can add them by adding the
`extraWhitelistPaths` node to your configuration YAML file.
Trino Gateway takes regexes from `extraWhitelistPaths` and forwards only
those requests with a URI that exactly match. Be sure
to use single-quoted strings so that escaping is not required.

```yaml
extraWhitelistPaths:
  - '/ui/insights'
  - '/api/v1/biac'
  - '/api/v1/dataProduct'
  - '/api/v1/dataproduct'
  - '/api/v2/.*'
  - '/ext/faster'
```

### Configure additional v1/statement-like paths

The Trino client protocol specifies that queries are initiated by a POST to `v1/statement`. 
The Trino Gateway incorporates this into its routing logic by extracting and recording the 
query id from responses to such requests. If you use an experimental or commercial build of
Trino that supports additional endpoints, you can cause Trino Gateway to treat them 
equivalently to `/v1/statement` by adding them under the `additionalStatementPaths`
configuration node. They must be absolute, and no path can be a prefix to any other path.
The standard `/v1/statement` path is always included and does not need to be configured. 
For example:

```yaml
additionalStatementPaths:
  - '/ui/api/insights/ide/statement'
  - '/v2/statement'
```

## Configure behind a load balancer

A possible deployment of Trino Gateway is to run multiple instances of Trino 
Gateway behind another generic load balancer, such as a load balancer from 
your cloud hosting provider. In this deployment you must configure the 
`serverConfig` to include enabling process forwarded HTTP headers:

```yaml
serverConfig:
  http-server.process-forwarded: true
```

## Configure larger proxy response size

Trino Gateway reads the response from Trino in bytes (up to 32MB by default).
It can be configured by setting:

```yaml
proxyResponseConfiguration:
  responseSize: 50MB
```

## Running Trino Gateway

Start Trino Gateway with the following java command in the directory of the
JAR and YAML files:

```shell
java -XX:MinRAMPercentage=50 -XX:MaxRAMPercentage=80 \
    -jar gateway-ha.jar gateway-ha-config.yml
```

### Helm <a name="helm"></a>

Helm manages the deployment of Kubernetes applications by templating Kubernetes
resources with a set of Helm charts. The Trino Gateway Helm chart is 
available in the [Trino Helm chart project](https://github.com/trinodb/charts).

Configure the charts repository as a Helm chart repository with the 
following command:

```shell
helm repo add trino https://trinodb.github.io/charts/
```
The Trino Gateway chart consists of the following components:

* A `config` node for general configuration
* `dataStoreSecret`, `backendStateSecret` and `authenticationSecret` for 
  providing sensitive configurations through Kubernetes secrets, 
* Standard Helm options such as `replicaCount`, `resources` and `ingress`.

The default `values.yaml` found in the `helm/trino-gateway` folder includes
basic configuration options as an example. For a simple deployment, proceed with 
the following steps:

Create a yaml file containing the configuration for your `datastore`:

```shell
cat << EOF > datastore.yaml
dataStore:
   jdbcUrl: jdbc:postgresql://yourdatabasehost:5432/gateway
   user: postgres
   password: secretpassword
   driver: org.postgresql.Driver
EOF
```
Create a Kubernetes secret from this file:

```shell
kubectl create secret generic datastore-yaml --from-file datastore.yaml --dry-run=client -o yaml | kubectl apply -f -
```

Create a values override with a name such as `values-override.yaml` and
reference this secret in the `backendStateSecret` node:

```yaml
backendStateSecret:
    name: "datastore-yaml"
    key: "datastore.yaml"
```

When a Secret is created with the `--from-file` option, the filename is used as
the key. Finally, you can deploy Trino Gateway with the chart from the root 
of this repository:

```shell
helm install tg --values values-override.yaml helm/trino-gateway 
```

Secrets for `authenticationSecret` and `backendState` can be provisioned
similarly. Alternatively,  you can directly define the `config.backEndState` 
node in `values-override.yaml` and leave `backendStateSecret` undefined. 
However, a [Secret](https://kubernetes.io/docs/concepts/configuration/secret/)
is recommended to protect the database credentials required for this 
configuration.

By default, the Trino Gateway process is started with the following command:

```shell
java -XX:MinRAMPercentage=80.0 -XX:MaxRAMPercentage=80.0 -jar /usr/lib/trino/gateway-ha-jar-with-dependencies.jar /etc/gateway/config.yaml
```

You can customize details with the `command` node. It accepts a list, that must
begin with an executable such as `java` or `bash` that is available on the PATH.
The following list elements are provided as arguments to the executable. It is
not typically necessary to modify this node. You can use it to change of JVM
startup parameters to control memory settings and other aspects, or to use other
configuration file names.

#### Additional options

To implement routing rules, create a ConfigMap from your routing rules yaml
definition:

```shell
kubectl create cm routing-rules --from-file your-routing-rules.yaml
```

Then mount it to your container:

```yaml
volumes:
    - name: routing-rules
      configMap:
          name: routing-rules
          items:
              name: your-routing-rules.yaml
              path: your-routing-rules.yaml

volumeMounts:
    - name: routing-rules
      mountPath: "/etc/routing-rules/your-routing-rules.yaml"
      subPath: your-routing-rules.yaml
```

Ensure that the `mountPath` matches the `rulesConfigPath` specified in your
configuration. Note that the `subPath` is not strictly necessary, and if it 
is not specified the file is mounted at `mountPath/<configMap key>`. 
Kubernetes updates the mounted file when the ConfigMap is updated.

Standard Helm options such as `replicaCount`, `image`, `imagePullSecrets`, 
`service`, `ingress` and `resources` are supported. These are defined in 
`helm/values.yaml`. 

More detail about the chart are available in the [values 
reference documentation](https://github.com/trinodb/charts/blob/main/charts/gateway/README.md)

### Health Checks

The Trino Gateway periodically performs health checks and maintains
an in-memory TrinoStatus for each backend. If a backend fails a health check,
it is marked as UNHEALTHY, and the Trino Gateway stops routing requests to it.

It is important to distinguish TrinoStatus from the active/inactive
state of a backend. The active/inactive state indicates whether a backend is
manually turned on or off, whereas TrinoStatus is programmatically determined
by the health check process. Health checks are only performed on backends
that are marked as active.

See [TrinoStatus](routing-rules.md#trinostatus) for more details on 
what each Trino status means.

The type of health check is configured by setting

```yaml
clusterStatsConfiguration:
  monitorType: ""
```

to one of the following values.

#### INFO_API (default)

By default Trino Gateway uses the `v1/info` REST endpoint. A successful check is
defined as a 200 response with `starting: false`. Connection timeout parameters 
can be defined through the `monitor` node, for example

```yaml
monitor:
  connectTimeoutSeconds: 5
  requestTimeoutSeconds: 10
  idleTimeoutSeconds: 1
  retries: 1
```

All timeout parameters are optional.

#### JDBC

This uses a JDBC connection to query `system.runtime` tables for cluster 
information. It is required for the query count based routing strategy. This is
recommended over `UI_API` since it does not restrict the Web UI authentication
method of backend clusters. Configure a username and password by adding
`backendState` to your configuration. The username and password must be valid 
across all backends.

Trino Gateway uses `explicitPrepare=false` by default. This property was introduced
in Trino 431, and uses a single query for prepared statements, instead of a 
`PREPARE/EXECUTE` pair. If you are using the JDBC health check option with older 
versions of Trino, set
```yaml
monitorConfiguration:
   explicitPrepare: false
```

```yaml
backendState:
  username: "user"
  password: "password"
```

The query timeout can be set through

```yaml
monitor:
    queryTimeout: 10
```

Other timeout parameters are not applicable to the JDBC connection.

#### JMX

The monitor type `JMX` can be used as an alternative to collect cluster information, 
which is required for the `QueryCountBasedRouterProvider`. This uses the `v1/jmx/mbean` 
endpoint on Trino clusters.

To enable this:

[JMX monitoring](https://trino.io/docs/current/admin/jmx.html) must be activated on all Trino clusters with:

```properties
jmx.rmiregistry.port=<port>
jmx.rmiserver.port=<port>
```

Allow JMX endpoint access by adding rules to your [file-based access control](https://trino.io/docs/current/security/file-system-access-control.html)
configuration. Example for `user`:

```json
{  
  "catalogs": [
    {
      "user": "user",
      "catalog": "system",
      "allow": "read-only"
    }
  ],
  "system_information": [
    {
      "user": "user",
      "allow": ["read"]
    }
  ]
}
```

Ensure that a username and password are configured by adding the `backendState`
section to your configuration. The credentials must be consistent across all
backend clusters and have `read` rights on the `system_information`.

```yaml
backendState:
  username: "user"
  password: "password"
```

The JMX monitor will use these credentials to authenticate against the
JMX endpoint of each Trino cluster and collect metrics like running queries,
queued queries, and worker nodes information.

#### UI_API

This pulls cluster information from the `ui/api/stats` REST endpoint. This is
supported for legacy reasons and may be deprecated in the future. It is only 
supported for backend clusters with `web-ui.authentication.type=FORM`. Set
a username and password using `backendState` as with the `JDBC` option.

#### NOOP

This option disables health checks.
