# Migration to Airlift

The codebase of Trino Gateway has been heavily upgraded to meet the Trino
standards. Dropwizard and Jetty Proxy has been replaced by Airbase and Airlift.
This page documents the incompatible changes and aims to smooth the migration.
The migration is only required for user using Trino Gateway before version 10.

## Change of endpoints

You have to make the following adjustments to take the change to using only one
HTTP port into account.

### Merge of service ports

Multiple service ports have been merged together. The old version
listens to multiple ports: request service at port 9080, application service
at port 9081, and the Admin service at port 9082. The new version only
listens to one single port at 8080 by default.

### Merge of request service and application service

Requests to the Trino Gateway (Web UI and RESTful API) and requests that need to
be forwarded to Trino are both served by the same port. The destination is
determined by the HTTP URI in the request.

### Removal of the Dropwizard admin service at port 9082

The admin console page provided by Dropwizard (port 9082) has been removed.
JMX can be viewed at `/v1/jmx`. There are no plans to support other functions on
the Dropwizard Admin page (metrics, ping, threads, healthcheck, CPU profile,
and CPU contention).

### Path change for WebUI

The root path for Trino Gateway WebUI has been changed from `/` to
`/trino-gateway`. By default, access to `/` is redirected to `/trino-gateway`.
This behavior can be changed by adding `/` to `extraWhitelistPaths`, which
results in routing `/` to Trino.

## Change of configuration

You must adjust your configuration to the following changes.

### Change of service ports

`requestRouter`, `server`, `applicationConnectors`, and `adminConnectors`
are replaced by `serverConfig`.

Old config:
```yaml
requestRouter:
    port: 8080
    name: trinoRouter
    historySize: 1000
    requestBufferSize: 8192
server:
    applicationConnectors:
        - type: http
          port: 8090
          useForwardedHeaders: true
    adminConnectors:
        - type: http
          port: 8091
          useForwardedHeaders: true
```

New config:
```yaml
serverConfig:
    node.environment: test
    http-server.http.port: 8443
    proxy.http-client.request-buffer-size: 8kB
```

### TLS configuration

`ssl`, `keystorePath`, and `keystorePass` are replaced by
`http-server.https.*`. For more details, see [Security](security.md)

Old config:
```yaml
requestRouter:
  ssl: true
  port: 8080
  name: trinoRouter
  historySize: 1000
  keystorePath: <path>
  keystorePass: <password>

server:
  applicationConnectors:
    - type: https
      port: 8090
      keyStorePath: <path>
      keyStorePassword: <password>
      useForwardedHeaders: true
  adminConnectors:
    - type: https
      port: 8091
      keyStorePath: <path>
      keyStorePassword: <password>
      useForwardedHeaders: true
```

New config:
```yaml
serverConfig:
    http-server.http.enabled: false
    http-server.https.enabled: true
    http-server.https.port: 8080
    http-server.https.keystore.path: <path>
    http-server.https.keystore.key: <password>
```

### Logging

Old config:

* Set logging to external
```yaml
logging:
    type: external
```
* Also specify the path to the `log.properties` file using the `log.levels-file`
  JVM options, such as `-Dlog.levels-file=etc/log.properties`.

New config:

* Specify the path to the `log.properties` file in config
```yaml
serverConfig:
    log.levels-file: gateway-ha/etc/log.properties
```
*  The `log.levels-file` JVM option is no longer supported.

### JVM startup arguments

The first arg `server` has been removed.

Old config:
```bash
java -jar gateway-ha.jar server gateway-config.yml
```

New config:
```bash
java -jar gateway-ha.jar gateway-config.yml
```

### Format of `extraWhitelistPaths`

The path setting in `extraWhitelistPaths` is now a regex that matches the full
URI in the request. The old version forwards requests with a URI prefix that
matches any path in `extraWhitelistPaths`. The new version takes regexes from
`extraWhitelistPaths` and forwards only those requests with a URI that exactly
matches any of the regexes. This change is required because in the old
version, `/` matches and forwards every request.

Be sure to use single-quoted strings so that escaping is not required. The
following configurations are equivalent.

Old config:
```yaml
extraWhitelistPaths:
    - "/ui"
    - "/v1/custom"
```

New config:
```yaml
extraWhitelistPaths:
    - '/ui.*'
    - '/v1/custom.*'
```

### JVM options

The following JVM options are no longer required:
```
-Dlog.levels-file=XXX
--add-opens=java.base/java.lang=ALL-UNNAMED
--add-opens=java.base/java.net=ALL-UNNAMED
```

## Miscellaneous changes

The following section details a number of other changes.

### Use port 8080 and 8443

Use port 8080 for HTTP and 8443 for HTTPS in all the documentations, Docker
image and example config file. The use of the ports 9080, 9081, 9082 has been
removed. This enhances the consistency throughout the project.

### Request logging

`dropwizard-request-logging` has been removed. Airlift provides logging for the 
HTTP requests. See
[Logging properties](https://trino.io/docs/current/admin/properties-logging.html#http-server-log-enabled) 
for configuration.
