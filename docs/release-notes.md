# Release notes

## 2026

### Trino Gateway 23 (Planned Date) { id="23" }

Artifacts:

* [JAR file gateway-ha-23-jar-with-dependencies.jar](https://repo1.maven.org/maven2/io/trino/gateway/gateway-ha/23/gateway-ha-23-jar-with-dependencies.jar)
* Container image `trinodb/trino-gateway:23`
* Source code as
  [tar.gz](https://github.com/trinodb/trino-gateway/archive/refs/tags/23.tar.gz)
  or [zip](https://github.com/trinodb/trino-gateway/archive/refs/tags/23.zip)
* [Trino Helm chart](https://trinodb.github.io/charts/) `trino/trino-gateway` version `1.23.0`

Changes:

**General**

* Add support for
  [rejecting requests instead of routing them to the default routing group](routing-rules.md)
  with the `routing.unmatchedRequestFallbackEnabled` and
  `routing.unavailableGroupFallbackEnabled` configuration properties.
  ([#1319](https://github.com/trinodb/trino-gateway/issues/1319))
* Add [gzip compression](installation.md#configure-http-compression) for
  responses from Trino clusters. This can be deactivated by setting the
  `proxy.http-client.gzip-compression.enabled` configuration property to
  `false`.
  ([#765](https://github.com/trinodb/trino-gateway/pull/765))
* [:warning: Breaking change:](#breaking) Add a
  [session timeout for form authentication](security.md#formbasic-authentication),
  which ends sessions 30 minutes after login by default. Set the
  `sessionTimeout` configuration property to change the duration. Users must
  log in again after the upgrade.
  ([#717](https://github.com/trinodb/trino-gateway/issues/717))
* Add support for EC keys in [JWT authentication](security.md#jwt-authentication)
  and in OIDC ID token verification.
  ([#1425](https://github.com/trinodb/trino-gateway/issues/1425))
* Add support for
  [buffering the query history in memory](installation.md#query-history-write-buffer)
  while the database is unavailable, with the `writeBuffer.enabled`
  configuration property.
  ([#794](https://github.com/trinodb/trino-gateway/pull/794))
* Add support for extracting the user from Trino OAuth2
  [refresh tokens](routing-rules.md#trinorequestuser) with the
  `requestAnalyzerConfig.oauthRefreshTokensSecretKey` configuration property.
  ([#1402](https://github.com/trinodb/trino-gateway/issues/1402))
* Add support for choosing the claim of the
  [user info response](routing-rules.md#trinorequestuser) that is used as the
  user, with the `requestAnalyzerConfig.oauthTokenInfoUserField` configuration
  property.
  ([#1342](https://github.com/trinodb/trino-gateway/pull/1342))
* Add support for reading the `USING` parameters of `EXECUTE` and
  `EXECUTE IMMEDIATE` statements in [routing rules](routing-rules.md#trinoqueryproperties)
  with `getBoundBody()`.
  ([#1334](https://github.com/trinodb/trino-gateway/issues/1334))
* Add support for setting the number of
  [threads for health checks](installation.md#health-checks-on-trino-clusters)
  with the `monitor.threadPoolSize` configuration property.
  ([#1405](https://github.com/trinodb/trino-gateway/pull/1405))
* [:warning: Breaking change:](#breaking) Add the `unqualifiedNames` and
  `boundBody` fields to the query properties that Trino Gateway sends to an
  [external routing service](routing-rules.md). Update services that reject
  unknown fields.
  ([#1333](https://github.com/trinodb/trino-gateway/issues/1333))
  ([#1334](https://github.com/trinodb/trino-gateway/issues/1334))
* [:warning: Breaking change:](#breaking) Reject
  [routing rules](routing-rules.md#configure-routing-rules-with-a-file) that use
  reflection, class loading, `import` statements, or classes other than the
  documented ones. Rewrite affected rules to use only the documented classes.
  ([#1374](https://github.com/trinodb/trino-gateway/pull/1374))
* [:warning: Breaking change:](#breaking) Require a user with the `ADMIN` or
  `API` role for the [metrics and JMX endpoints](operation.md#monitoring) when
  authentication is configured. Add the credentials of such a user to the
  configuration of Prometheus or other monitoring systems. The same applies to
  resources of custom modules without a `@RolesAllowed` annotation.
  ([#1450](https://github.com/trinodb/trino-gateway/pull/1450))
* [:warning: Breaking change:](#breaking) Check the
  [health of Trino clusters](installation.md#health-checks-on-trino-clusters)
  only for active Trino clusters by default, and report deactivated Trino
  clusters with the `UNKNOWN` status. Set the `monitor.checkInactiveBackends`
  configuration property to `true` to also check deactivated Trino clusters.
  ([#1404](https://github.com/trinodb/trino-gateway/pull/1404))
* Update the Java runtime in the Docker image to Amazon Corretto 25.0.5.1.2 to
  fix CVE-2026-114938.
  ([#1438](https://github.com/trinodb/trino-gateway/issues/1438))
* Return HTTP 503 instead of HTTP 502 when a request fails because its Trino
  cluster is deactivated, such as a cluster scaled down to zero.
  ([#1156](https://github.com/trinodb/trino-gateway/pull/1156))
* Return HTTP 403 instead of HTTP 200 for failed logins to the web UI, and log
  them.
  ([#1453](https://github.com/trinodb/trino-gateway/pull/1453))
* Return HTTP 403 instead of HTTP 200 when a user lacks the role for a request
  to the web UI or the Trino Gateway API.
  ([#1484](https://github.com/trinodb/trino-gateway/issues/1484))
* Improve performance of the query history page when filtering by user.
  ([#1421](https://github.com/trinodb/trino-gateway/pull/1421))
* Reduce load on Trino clusters from requests for unknown queries.
  ([#1442](https://github.com/trinodb/trino-gateway/pull/1442))
* Prevent other websites from using the web UI login session of a user to send
  requests to Trino Gateway.
  ([#1377](https://github.com/trinodb/trino-gateway/pull/1377))
* Prevent the web UI login session cookie from overriding the credentials in
  the `Authorization` header of requests to the web UI and the Trino Gateway
  API.
  ([#1451](https://github.com/trinodb/trino-gateway/pull/1451))
* Prevent requests from hanging when the
  [user info endpoint](routing-rules.md#trinorequestuser) in
  `requestAnalyzerConfig.oauthTokenInfoUrl` does not respond. Set the timeout
  with the `requestAnalyzerConfig.oauthRequestTimeout` configuration property,
  `5s` by default.
  ([#1330](https://github.com/trinodb/trino-gateway/pull/1330))
* Prevent routing to Trino clusters whose
  [health check](installation.md#health-checks-on-trino-clusters) does not
  complete within the monitor `taskDelay`, and prevent a slow health check from
  delaying the status updates of other Trino clusters.
  ([#1405](https://github.com/trinodb/trino-gateway/pull/1405))
* Prevent sending headers listed in `excludeHeaders` to the
  [external routing service](routing-rules.md#use-an-external-service-for-routing-rules)
  when the client uses a different casing for the header name, such as
  `authorization`.
  ([#1419](https://github.com/trinodb/trino-gateway/pull/1419))
* Prevent logging the values of headers that the external routing service
  changes.
  ([#1419](https://github.com/trinodb/trino-gateway/pull/1419))
* Fix internal error when the default routing group has no healthy Trino
  cluster. The request fails with HTTP 503 instead.
  ([#1320](https://github.com/trinodb/trino-gateway/pull/1320))
* Fix the web UI login session staying in the browser after a user logs out
  with [OAuth authentication](security.md#oauthopenidconnect).
  ([#1443](https://github.com/trinodb/trino-gateway/pull/1443))
* Fix internal error for `system.runtime.kill_query` calls when
  `requestAnalyzerConfig.analyzeRequest` is `false`.
  ([#1442](https://github.com/trinodb/trino-gateway/pull/1442))
* Fix request failures when the `requestAnalyzerConfig.tokenUserField` claim
  of a JWT bearer token is not a string, such as a number.
  ([#1492](https://github.com/trinodb/trino-gateway/pull/1492))
* Fix query submission failures when the query history cannot be written to
  the database.
  ([#1418](https://github.com/trinodb/trino-gateway/pull/1418))
* Fix corruption of binary responses from Trino clusters, such as the segments
  of the spooling protocol.
  ([#1279](https://github.com/trinodb/trino-gateway/pull/1279))
* Fix truncated responses when a response from a Trino cluster is larger than
  the `proxyResponseConfiguration.responseSize` configuration property. The
  request fails instead.
  ([#1279](https://github.com/trinodb/trino-gateway/pull/1279))
* Fix the `METRICS` cluster monitor for Trino 477 and later, which renamed the
  metric for the number of active nodes.
  ([#1155](https://github.com/trinodb/trino-gateway/issues/1155))
* Fix the `UI_API` cluster monitor for Trino 483 and later, which changed the
  login of the Trino web UI.
  ([#1219](https://github.com/trinodb/trino-gateway/issues/1219))
* Fix routing of `ALTER TABLE`, `ALTER VIEW`, and `ALTER MATERIALIZED VIEW`
  statements that rename to a fully qualified name, or rename a materialized
  view without a catalog.
  ([#1389](https://github.com/trinodb/trino-gateway/pull/1389))
* Fix failures of query parsing for routing rules when a query uses names that
  cannot be qualified from the request headers. These names are available with
  `getUnqualifiedNames()` in [routing rules](routing-rules.md).
  ([#1333](https://github.com/trinodb/trino-gateway/issues/1333))
* Fix duplicated headers sent to Trino clusters when the external routing
  service returns a header with a different casing than the client request.
  ([#1419](https://github.com/trinodb/trino-gateway/pull/1419))

**UI**

* Add support for linking queries in the query history to the
  [web UI of Trino 483 and later](installation.md#link-queries-to-the-trino-web-ui)
  with the `uiConfiguration.queryLinkType` configuration property.
  ([#1331](https://github.com/trinodb/trino-gateway/issues/1331))
* Update the dashboard charts with a new color palette and move the legend
  below the charts.
  ([#1326](https://github.com/trinodb/trino-gateway/pull/1326))
* Fix error on the routing rules page when the rules engine is not in use.
  ([#1328](https://github.com/trinodb/trino-gateway/issues/1328))

More details and a list of all merged pull requests are [available in the milestone 23
list](https://github.com/trinodb/trino-gateway/pulls?q=is%3Apr+milestone%3A23+is%3Aclosed)
and the [GitHub release section for version
23](https://github.com/trinodb/trino-gateway/releases/tag/23).

### Trino Gateway 22 (20 Oct 2026) { id="22" }

Artifacts:

* [JAR file gateway-ha-22-jar-with-dependencies.jar](https://repo1.maven.org/maven2/io/trino/gateway/gateway-ha/22/gateway-ha-22-jar-with-dependencies.jar)
* Container image `trinodb/trino-gateway:22`
* Source code as
  [tar.gz](https://github.com/trinodb/trino-gateway/archive/refs/tags/22.tar.gz)
  or [zip](https://github.com/trinodb/trino-gateway/archive/refs/tags/22.zip)
* [Trino Helm chart](https://trinodb.github.io/charts/) `trino/trino-gateway` version `1.22.0`

Changes:

**General**

* Add support for configuring multiple
  [authentication methods](security.md#authentication) as an ordered list with
  fallback in the `authentication.defaultType` configuration property.
  ([#1170](https://github.com/trinodb/trino-gateway/pull/1170))
* Add support for [JWT authentication](security.md#jwt-authentication) with
  bearer tokens in the `Authorization` header, including
  [user mapping](security.md#user-mapping) of the token principal.
  ([#449](https://github.com/trinodb/trino-gateway/issues/449))
* Add support for
  [routing all requests of a Trino OAuth2 login](installation.md#configure-oauth2-token-exchange-routing)
  to the coordinator that started it with the `routing.oauth2RoutingEnabled`
  configuration property.
  ([#631](https://github.com/trinodb/trino-gateway/issues/631) and
  [#892](https://github.com/trinodb/trino-gateway/issues/892))
* Add support for attaching [tags to Trino clusters](backend-tags.md).
  ([#947](https://github.com/trinodb/trino-gateway/pull/947))
* Add support for
  [pooling database connections](installation.md#backend-database) with the
  `dataStore.maxPoolSize`, `dataStore.keepaliveTime`, and `dataStore.maxLifetime`
  configuration properties.
  ([#377](https://github.com/trinodb/trino-gateway/issues/377))
* Add support for [granting default privileges](security.md#default-privileges)
  to all authenticated users with the `authorization.enableDefaultPrivilege` and
  `authorization.defaultPrivilege` configuration properties.
  ([#1170](https://github.com/trinodb/trino-gateway/pull/1170))
* Add support for
  [limiting the length of query text](installation.md#backend-database) stored
  in the query history with the
  `dataStore.queryHistoryMaxQueryTextLength` configuration property.
  ([#1311](https://github.com/trinodb/trino-gateway/issues/1311))
* Add support for suppressing subordinate referrals when
  [LDAP searches](security.md#authorization) start at the root of a Microsoft
  Active Directory domain with the `ldapAdDomainScopeControl` configuration
  property.
  ([#1173](https://github.com/trinodb/trino-gateway/issues/1173))
* Add support for `ALTER MATERIALIZED VIEW ... EXECUTE` statements in
  [routing rules based on query properties](routing-rules.md#trinoqueryproperties).
  ([#1340](https://github.com/trinodb/trino-gateway/pull/1340))
* Automatically manage all database schema changes and remove the
  `gateway-ha-persistence-mysql.sql` and `gateway-ha-persistence-postgres.sql`
  database initialization scripts from the JAR file. When [database migrations
  are deactivated](installation.md#backend-database) with `runMigrationsEnabled`
  set to `false`, create the database schema with the migration files instead.
  ([#1304](https://github.com/trinodb/trino-gateway/pull/1304))
* Fix routing to the default routing group instead of the group selected by
  routing rules for prepared statements longer than 2048 characters.
  ([#1289](https://github.com/trinodb/trino-gateway/pull/1289))

**UI**

* Add an [authentication method](security.md#authentication) selector to the
  login page. Show only the first method by setting the
  `authentication.showFirstTypeOnly` configuration property to `true`.
  ([#1170](https://github.com/trinodb/trino-gateway/pull/1170))
* Add support for viewing the tags of Trino clusters in the cluster list, and
  for editing them when adding or updating a cluster.
  ([#947](https://github.com/trinodb/trino-gateway/pull/947))
* Show the name of the cluster in the confirmation dialog for deleting a
  cluster.
  ([#1280](https://github.com/trinodb/trino-gateway/pull/1280))

More details and a list of all merged pull requests are [available in the milestone 22
list](https://github.com/trinodb/trino-gateway/pulls?q=is%3Apr+milestone%3A22+is%3Aclosed)
and the [GitHub release section for version
22](https://github.com/trinodb/trino-gateway/releases/tag/22).

### Trino Gateway 21 (25 Aug 2026) { id="21" }

Artifacts:

* [JAR file gateway-ha-21-jar-with-dependencies.jar](https://repo1.maven.org/maven2/io/trino/gateway/gateway-ha/21/gateway-ha-21-jar-with-dependencies.jar)
* Container image `trinodb/trino-gateway:21`
* Source code as
  [tar.gz](https://github.com/trinodb/trino-gateway/archive/refs/tags/21.tar.gz)
  or [zip](https://github.com/trinodb/trino-gateway/archive/refs/tags/21.zip)
* [Trino Helm chart](https://trinodb.github.io/charts/) `trino/trino-gateway` version `1.21.0`

Changes:

**General**

* Support direct LDAP bind without a user search.
  ([#1205](https://github.com/trinodb/trino-gateway/pull/1205))
* Add support for client certificate authentication with a JWT bridge that
  forwards the mapped user identity to the backend clusters as a signed token.
  ([#1022](https://github.com/trinodb/trino-gateway/pull/1022))
* Add support for elliptic curve key pairs for JWT signing in addition to RSA
  key pairs.
  ([#1022](https://github.com/trinodb/trino-gateway/pull/1022))
* [:warning: Breaking change:](#breaking) Rename the `selfSignKeyPair`
  configuration properties `privateKeyRsa` and `publicKeyRsa` to `privateKey`
  and `publicKey`.
  ([#1022](https://github.com/trinodb/trino-gateway/pull/1022))
* Fix parsing of SQL request bodies that omit the charset in the `Content-Type`
  header.
  ([#1164](https://github.com/trinodb/trino-gateway/issues/1164))
* Fix Trino spooling client protocol use by routing spooled-result download and
  acknowledgement requests to Trino coordinators.
  ([#1033](https://github.com/trinodb/trino-gateway/issues/1033))
* Prevent routing to stale or unverified backends by refreshing the backend
  health state after API requests modify the backend configuration.
  ([#1029](https://github.com/trinodb/trino-gateway/pull/1029))
* Fix content-based routing for additional SQL statements by extracting their
  table, catalog, and schema references.
  ([#1145](https://github.com/trinodb/trino-gateway/pull/1145))
* [:warning: Breaking change:](#breaking) Stop building, testing, and shipping
  container images for PowerPC (ppc64le) processors.
  ([#1198](https://github.com/trinodb/trino-gateway/pull/1198))
* [:warning: Breaking change:](#breaking) Fail startup when the
  `http-server.http.port` configuration property is set while
  `http-server.http.enabled` is `false`, since the port is no longer read when
  the HTTP listener is disabled. Remove the property from `serverConfig` in
  HTTPS-only deployments.
  ([#1162](https://github.com/trinodb/trino-gateway/pull/1162))

More details and a list of all merged pull requests are [available in the
milestone 21
list](https://github.com/trinodb/trino-gateway/pulls?q=is%3Apr+milestone%3A21+is%3Aclosed)
and the [GitHub release section for version
21](https://github.com/trinodb/trino-gateway/releases/tag/21).

### Trino Gateway 20 (25 Jun 2026) { id="20" }

Artifacts:

* [JAR file gateway-ha-20-jar-with-dependencies.jar](https://repo1.maven.org/maven2/io/trino/gateway/gateway-ha/20/gateway-ha-20-jar-with-dependencies.jar)
* Container image `trinodb/trino-gateway:20`
* Source code as
  [tar.gz](https://github.com/trinodb/trino-gateway/archive/refs/tags/20.tar.gz)
  or [zip](https://github.com/trinodb/trino-gateway/archive/refs/tags/20.zip)
* [Trino Helm chart](https://trinodb.github.io/charts/) `trino/trino-gateway` version `1.20.0`

Changes:

**General**

* Fix runtime Jetty compression failures with the Docker image.
  ([#1094](https://github.com/trinodb/trino-gateway/pull/1094))
* Improve security of the `/webapp/findQueryHistory` endpoint.
  ([#991](https://github.com/trinodb/trino-gateway/issues/991))
* Prevent silent routing mistakes for `SHOW BRANCHES`, `CREATE`/`DROP BRANCH`,
  `REFRESH VIEW`, and column `SET`/`DROP DEFAULT` statements.
  ([#1121](https://github.com/trinodb/trino-gateway/pull/1121))

More details and a list of all merged pull requests are [available in the
milestone 20
list](https://github.com/trinodb/trino-gateway/pulls?q=is%3Apr+milestone%3A20+is%3Aclosed)
and the [GitHub release section for version
20](https://github.com/trinodb/trino-gateway/releases/tag/20).

### Trino Gateway 19 (11 May 2026) { id="19" }

Artifacts:

* [JAR file gateway-ha-19-jar-with-dependencies.jar](https://repo1.maven.org/maven2/io/trino/gateway/gateway-ha/19/gateway-ha-19-jar-with-dependencies.jar)
* Container image `trinodb/trino-gateway:19`
* Source code as
  [tar.gz](https://github.com/trinodb/trino-gateway/archive/refs/tags/19.tar.gz)
  or [zip](https://github.com/trinodb/trino-gateway/archive/refs/tags/19.zip)
* [Trino Helm chart](https://trinodb.github.io/charts/) `trino/trino-gateway` version `1.19.0`

Changes:

**General**

* [:warning: Breaking change:](#breaking) Remove all resource group management
  functionality. Existing resource group database tables are preserved and not
  dropped on upgrade to avoid accidental data loss from Trino Gateway update.
  New Trino Gateway deployments no longer create or manage these tables.
  Resource groups are a Trino feature and must be managed through Trino
  directly.
  ([#656](https://github.com/trinodb/trino-gateway/issues/656))
* [:warning: Breaking change:](#breaking) Rename routing configuration
  `addXForwardedHeaders` to `forwardedHeadersEnabled`.
  ([#1005](https://github.com/trinodb/trino-gateway/pull/1005))
* Fix HTTP header forwarding when `routing.forwardedHeadersEnabled` is set to
  `false` to properly drop both the modern RFC 7239 `Forwarded` and the legacy
  `X-Forwarded-*` HTTP headers.
  ([#1005](https://github.com/trinodb/trino-gateway/pull/1005))
* Fix issue with eager evaluation of fallback routing for queries. 
  ([#922](https://github.com/trinodb/trino-gateway/pull/922))
* Prevent SQL analysis failures due to missing character encoding.
  ([#1032](https://github.com/trinodb/trino-gateway/issues/1032))

**UI**

* Fix rendering of the query distribution chart.
  ([#942](https://github.com/trinodb/trino-gateway/pull/942))
* Improve application name display in header
  ([#1003](https://github.com/trinodb/trino-gateway/pull/1003))

More details and a list of all merged pull requests are [available in the
milestone 19
list](https://github.com/trinodb/trino-gateway/pulls?q=is%3Apr+milestone%3A19+is%3Aclosed)
and the [GitHub release section for version
19](https://github.com/trinodb/trino-gateway/releases/tag/19).

### Trino Gateway 18 (4 Mar 2026) { id="18" }

Artifacts:

* [JAR file gateway-ha-18-jar-with-dependencies.jar](https://repo1.maven.org/maven2/io/trino/gateway/gateway-ha/18/gateway-ha-18-jar-with-dependencies.jar)
* Container image `trinodb/trino-gateway:18`
* Source code as
  [tar.gz](https://github.com/trinodb/trino-gateway/archive/refs/tags/18.tar.gz)
  or [zip](https://github.com/trinodb/trino-gateway/archive/refs/tags/18.zip)
* [Trino Helm chart](https://trinodb.github.io/charts/) `trino/trino-gateway` version `1.18.0`

Changes:

**General**

* Support in-memory caching of backend metadata retrieved from the Trino Gateway
  database with the `databaseCache` setting.
  ([#783](https://github.com/trinodb/trino-gateway/pull/783))
* Add `queryHistoryEnabled` configuration property to deactivate query history
  recording in the Trino Gateway database.
  ([#800](https://github.com/trinodb/trino-gateway/pull/800))
* Prevent failure of HTTP HEAD requests to `/v1/statement`.
  ([#937](https://github.com/trinodb/trino-gateway/pull/937))

**UI**

* Add a time zone selection dropdown for controlling the display time zone.
  ([#805](https://github.com/trinodb/trino-gateway/pull/805))

More details and a list of all merged pull requests are [available in the
milestone 18
list](https://github.com/trinodb/trino-gateway/pulls?q=is%3Apr+milestone%3A18+is%3Aclosed)
and the [GitHub release section for version
18](https://github.com/trinodb/trino-gateway/releases/tag/18).

### Trino Gateway 17 (31 Jan 2026) { id="17" }

Artifacts:

* [JAR file gateway-ha-17-jar-with-dependencies.jar](https://repo1.maven.org/maven2/io/trino/gateway/gateway-ha/17/gateway-ha-17-jar-with-dependencies.jar)
* Container image `trinodb/trino-gateway:17`
* Source code as
  [tar.gz](https://github.com/trinodb/trino-gateway/archive/refs/tags/17.tar.gz)
  or [zip](https://github.com/trinodb/trino-gateway/archive/refs/tags/17.zip)
* [Trino Helm chart](https://trinodb.github.io/charts/) `trino/trino-gateway` version `1.17.0`

Changes:

**General**

* Use UBI10 micro as the base Docker image.
  ([#779](https://github.com/trinodb/trino-gateway/pull/779))
* Expose Trino cluster state via JMX.
  ([#782](https://github.com/trinodb/trino-gateway/pull/782))
* Check health status for all clusters regardless of activation status.
  ([#809](https://github.com/trinodb/trino-gateway/pull/809))
* [:warning: Breaking change:](#breaking) Require Java 25 to run Trino Gateway
  and use it in the container image.
  ([#802](https://github.com/trinodb/trino-gateway/pull/802))
* Remove defunct email notifier.
  ([#843](https://github.com/trinodb/trino-gateway/pull/843))
* Fix `partialCancel` statement path redirection.
  ([#776](https://github.com/trinodb/trino-gateway/pull/776))
* Fix issues with large HTTP headers by setting `max-request-header-size` and
  `max-response-header-size` parameters for HTTP client connections.
  ([#571](https://github.com/trinodb/trino-gateway/issues/571))

**UI**

* Display a truncated query text in the query history list.
  ([#806](https://github.com/trinodb/trino-gateway/pull/806))
* Use and require Node.js v24.
  ([#813](https://github.com/trinodb/trino-gateway/pull/813))
* Fix incorrect display of zero queries in the query distribution chart.
  ([#788](https://github.com/trinodb/trino-gateway/pull/788))

More details and a list of all merged pull requests are [available in the
milestone 17
list](https://github.com/trinodb/trino-gateway/pulls?q=is%3Apr+milestone%3A17+is%3Aclosed)
and the [GitHub release section for version
17](https://github.com/trinodb/trino-gateway/releases/tag/17).

## 2025

### Trino Gateway 16 (17 Sep 2025) { id="16" }

Artifacts:

* [JAR file gateway-ha-16-jar-with-dependencies.jar](https://repo1.maven.org/maven2/io/trino/gateway/gateway-ha/16/gateway-ha-16-jar-with-dependencies.jar)
* Container image `trinodb/trino-gateway:16`
* Source code as
  [tar.gz](https://github.com/trinodb/trino-gateway/archive/refs/tags/16.tar.gz)
  or [zip](https://github.com/trinodb/trino-gateway/archive/refs/tags/16.zip)
* [Trino Helm chart](https://trinodb.github.io/charts/) `trino/trino-gateway` version `1.16.0`

Changes:

**General**

* [:warning: Breaking change:](#breaking) Require Java 24 to run Trino Gateway
  and use it in the container image.
  ([#727](https://github.com/trinodb/trino-gateway/pull/727))
* Update database testing and therefore support to PostgreSQL version 17.
  ([#753](https://github.com/trinodb/trino-gateway/pull/753))
* Allow configuration of a default routing group and error propagation to client
  tools. ([#687](https://github.com/trinodb/trino-gateway/pull/687))
* Add an option to add the `X-Forwarded-Proto` HTTP header when fetching cluster
  stats to enable Trino Gateway use with clusters behind a TLS-terminations load
  balancer.
  ([#729](https://github.com/trinodb/trino-gateway/pull/729))  
* Improve error propagation when using an external routing service.
  ([#687](https://github.com/trinodb/trino-gateway/pull/687))
* Add support for HTTP header modifications by an external routing service.
  ([#646](https://github.com/trinodb/trino-gateway/pull/646))
* Add cluster activation status metric and emit to `/v1/jmx`.
  ([#673](https://github.com/trinodb/trino-gateway/pull/673))
* Improve properties handling for JDBC connection to the database.
  ([#651](https://github.com/trinodb/trino-gateway/pull/651))
* [:warning: Breaking change:](#breaking) Rename the task delay configuration
  for the monitor from `taskDelaySeconds` to `taskDelay` and to support Airlift
  duration string instead of second values only.
  ([#695](https://github.com/trinodb/trino-gateway/pull/695))

**UI**

* Display routing group name in the query history page.
  ([#607](https://github.com/trinodb/trino-gateway/pull/607))
* Rename `RoutedTo` to `Name` field in query history page.
  ([#639](https://github.com/trinodb/trino-gateway/pull/639))
* Use external URL for query routing history display.
  ([#693](https://github.com/trinodb/trino-gateway/pull/693))
* Add separate window for query text in query history page and enable display of
  the full query text. 
  ([#740](https://github.com/trinodb/trino-gateway/pull/740))
* Show counts for healthy and unhealthy clusters in the summary section of the 
  dashboard page.
  ([#758](https://github.com/trinodb/trino-gateway/pull/758))
* A column sorting to the clusters list on the dashboard page and column sorting
  and filtering to the query list on the query history page.
  ([#752](https://github.com/trinodb/trino-gateway/pull/752))
* Fix UI rendering issues on routing rules page.
  ([#660](https://github.com/trinodb/trino-gateway/pull/660))
* Fix bug for non-admin users seeing login errors when accessing the UI when
  page restrictions are configured.
  ([#664](https://github.com/trinodb/trino-gateway/pull/664))

More details and a list of all merged pull requests are [available in the
milestone 16 list](https://github.com/trinodb/trino-gateway/pulls?q=is%3Apr+milestone%3A16+is%3Aclosed).

### Trino Gateway 15 (12 Mar 2025) { id="15" }

Artifacts:

* [JAR file gateway-ha-15-jar-with-dependencies.jar](https://repo1.maven.org/maven2/io/trino/gateway/gateway-ha/15/gateway-ha-15-jar-with-dependencies.jar)
* Container image `trinodb/trino-gateway:15`
* Source code as
  [tar.gz](https://github.com/trinodb/trino-gateway/archive/refs/tags/15.tar.gz)
  or [zip](https://github.com/trinodb/trino-gateway/archive/refs/tags/15.zip)
* [Trino Helm chart](https://trinodb.github.io/charts/) `trino/trino-gateway` version `1.15.0`

Changes:

* Add pop up messages for operations in the user interface.
  ([#617](https://github.com/trinodb/trino-gateway/pull/617))
* Add support for configuring custom OpenMetrics from the Trino clusters for the
  metrics monitor health check.
  ([#621](https://github.com/trinodb/trino-gateway/pull/621))
* [:warning: Breaking change:](#breaking) Change paths and file names in the
  Docker image. Configuration file name is `/opt/trino-gateway/config.yaml` and 
  folder names use `trino-gateway` consistently.  
  ([#623](https://github.com/trinodb/trino-gateway/pull/623)) and 
  ([#628](https://github.com/trinodb/trino-gateway/pull/628))
* Prevent proxy failures resulting from backend URL configuration containing a 
  trailing slash.
  ([#564](https://github.com/trinodb/trino-gateway/issues/564))
* Fix query errors when adhoc routing group has no healthy backends.
  ([#630](https://github.com/trinodb/trino-gateway/pull/630)) and
  ([#641](https://github.com/trinodb/trino-gateway/issues/641))

More details and a list of all merged pull requests are [available in the
milestone 15 list](https://github.com/trinodb/trino-gateway/pulls?q=is%3Apr+milestone%3A15+is%3Aclosed).

### Trino Gateway 14 (14 Feb 2025) { id="14" }

Artifacts:

* [JAR file gateway-ha-14-jar-with-dependencies.jar](https://repo1.maven.org/maven2/io/trino/gateway/gateway-ha/14/gateway-ha-14-jar-with-dependencies.jar)
* Container image `trinodb/trino-gateway:14`
* Source code as
  [tar.gz](https://github.com/trinodb/trino-gateway/archive/refs/tags/14.tar.gz)
  or [zip](https://github.com/trinodb/trino-gateway/archive/refs/tags/14.zip)
* [Trino Helm chart](https://trinodb.github.io/charts/) `trino/trino-gateway` version `1.14.0`

Changes:

* Add display of the cluster health status on the cluster page.
  ([#601](https://github.com/trinodb/trino-gateway/pull/601))
* Add user interface page to show and edit routing rules.
  ([#433](https://github.com/trinodb/trino-gateway/pull/433))
* Add support for [automatic database schema validation and 
  upgrade](installation.md#backend-database) during Trino Gateway startup.
  ([#575](https://github.com/trinodb/trino-gateway/pull/575))
* Enable use of Oracle as backend database for Trino Gateway.
  ([#588](https://github.com/trinodb/trino-gateway/pull/588))
* Add support to configure HTTP client options for the external routing group
  selector. ([#580](https://github.com/trinodb/trino-gateway/pull/580))
* Add a cluster monitor using JMX data from Trino.
  ([#472](https://github.com/trinodb/trino-gateway/pull/472))
* Add a cluster monitor using the Trino OpenMetrics endpoint with customizable
  health definition. ([#584](https://github.com/trinodb/trino-gateway/pull/584))
* Add API health endpoints at `/trino-gateway/livez` and `/trino-gateway/readyz`
  for monitoring liveness and readiness.
  ([#595](https://github.com/trinodb/trino-gateway/pull/595))
* [:warning: Breaking change:](#breaking) Change configuration file name from 
  `gateway-ha-config.yml` to `config.yaml`.
  ([#598](https://github.com/trinodb/trino-gateway/issues/598))
* [:warning: Breaking change:](#breaking) Load fundamental modules by default, 
  and remove  the related configurations `HaGatewayProviderModule`, 
  `ActiveClusterMonitor`, `ClusterStateListenerModule`, and 
  `ClusterStatsMonitorModule` from the configuration file. 
  ([#597](https://github.com/trinodb/trino-gateway/pull/597))
* [:warning: Breaking change:](#breaking) Remove support for composite rules and
  the use of easy-rules syntax.
  ([#540](https://github.com/trinodb/trino-gateway/pull/540))
* Improve performance of Trino cluster stats collection and add a timeout
  configuration. ([#583](https://github.com/trinodb/trino-gateway/pull/583))
* Fix visibility of chart legend text when changing the theme of the user
  interface. ([#585](https://github.com/trinodb/trino-gateway/issues/585))

More details and a list of all merged pull requests are [available in the
milestone 14 list](https://github.com/trinodb/trino-gateway/pulls?q=is%3Apr+milestone%3A14+is%3Aclosed).

### Trino Gateway 13 (3 Dec 2024) { id="13" }

Artifacts:

* [JAR file gateway-ha-13-jar-with-dependencies.jar](https://repo1.maven.org/maven2/io/trino/gateway/gateway-ha/13/gateway-ha-13-jar-with-dependencies.jar)
* Container image `trinodb/trino-gateway:13`
* Source code as
  [tar.gz](https://github.com/trinodb/trino-gateway/archive/refs/tags/13.tar.gz)
  or [zip](https://github.com/trinodb/trino-gateway/archive/refs/tags/13.zip)
* [Trino Helm chart](https://trinodb.github.io/charts/) `trino/trino-gateway` version `1.13.0`

Changes:

* Allow proxying of HTTP PUT requests to Trino clusters.
  ([#543](https://github.com/trinodb/trino-gateway/pull/543))
* Add a filter for `source` to the **History** page in the UI.
  ([#551](https://github.com/trinodb/trino-gateway/pull/551))
* Log out inactive users from the UI automatically.
  ([#544](https://github.com/trinodb/trino-gateway/pull/544))

More details and a list of all merged pull requests are [available in the
milestone 13 list](https://github.com/trinodb/trino-gateway/pulls?q=is%3Apr+milestone%3A13+is%3Aclosed).


## 2024

### Trino Gateway 12 (7 Nov 2024) { id="12" }

Artifacts:

* [JAR file gateway-ha-12-jar-with-dependencies.jar](https://repo1.maven.org/maven2/io/trino/gateway/gateway-ha/12/gateway-ha-12-jar-with-dependencies.jar)
* Container image `trinodb/trino-gateway:12`
* Source code as
  [tar.gz](https://github.com/trinodb/trino-gateway/archive/refs/tags/12.tar.gz)
  or [zip](https://github.com/trinodb/trino-gateway/archive/refs/tags/12.zip)
* [Trino Helm chart](https://trinodb.github.io/charts/) `trino/trino-gateway` version `0.12.0`

Changes:

* Add support for parsing `EXECUTE IMMEDIATE` statements for routing.
  ([#484](https://github.com/trinodb/trino-gateway/issues/484))
* Add support to set configuration values from environment variables.
  ([#483](https://github.com/trinodb/trino-gateway/issues/483))
* Add support to include information about the cluster used for query processing
  to the response cookie.
  ([#465](https://github.com/trinodb/trino-gateway/issues/465))
* Add support for configuring the startup command for Trino Gateway in the
  Helm chart with the `command` node.
  ([#505](https://github.com/trinodb/trino-gateway/pull/505))
* [:warning: Breaking change:](#breaking) Require Java 23 for build and 
  runtime. Use Java 23 as runtime in the container.
  ([#486](https://github.com/trinodb/trino-gateway/issues/486))
* [:warning: Breaking change:](#breaking) Rename routing rule configuration
  `blackListHeaders` to`excludeHeaders`.
  ([#470](https://github.com/trinodb/trino-gateway/pull/470))
* Prevent request analyzer failures for some queries without a defined catalog.
  ([#478](https://github.com/trinodb/trino-gateway/issues/478))
* Fix parsing failure and therefore routing problems for queries using `WITH` 
  clauses.
  ([#528](https://github.com/trinodb/trino-gateway/issues/528))

More details and a list of all merged pull requests are [available in the
milestone 12 list](https://github.com/trinodb/trino-gateway/pulls?q=is%3Apr+milestone%3A12+is%3Aclosed).

### Trino Gateway 11 (12 Sep 2024) { id="11" }

Artifacts:

* [JAR file gateway-ha-11-jar-with-dependencies.jar](https://repo1.maven.org/maven2/io/trino/gateway/gateway-ha/11/gateway-ha-11-jar-with-dependencies.jar)
* Container image `trinodb/trino-gateway:11`
* Source code as
  [tar.gz](https://github.com/trinodb/trino-gateway/archive/refs/tags/11.tar.gz)
  or [zip](https://github.com/trinodb/trino-gateway/archive/refs/tags/11.zip)
* Helm chart `11.0.0` in `helm/trino-gateway` of the tagged source code

Changes:

* [:warning: Breaking change:](#breaking) Require Java 22 for build and runtime.
  ([#441](https://github.com/trinodb/trino-gateway/pull/441))
* Add support for determining routing group in an external service.
  ([#423](https://github.com/trinodb/trino-gateway/pull/423))
* Add an option to forward requests without adding `X-Forwarded-*` HTTP headers
  with the `addXForwardedHeaders: false` configuration in `routing`.
  ([#417](https://github.com/trinodb/trino-gateway/pull/417))
* Add OpenMetrics endpoint to
  [enable monitoring](operation.md#monitoring) with Prometheus and compatible systems.
  ([#429](https://github.com/trinodb/trino-gateway/pull/429))
* Add option to [deactivate hostname verification for the certificate 
  of the Trino clusters](security.md#cert-trino).
  ([#436](https://github.com/trinodb/trino-gateway/pull/436))
* Add option to use additional paths as Trino client REST API endpoints.
  ([#326](https://github.com/trinodb/trino-gateway/pull/326))
* Add timeout parameters for INFO_API and JDBC health checks.
  ([#424](https://github.com/trinodb/trino-gateway/pull/424))
* Add support for specifying custom labels in the Helm chart `commonLabels`.
  ([#448](https://github.com/trinodb/trino-gateway/pull/448))
* Enable routing for requests to kill query processing.
  ([#427](https://github.com/trinodb/trino-gateway/issues/427))
* Fix routing functionality and query history issues caused by lowercase 
  HTTP headers in HTTP/2 connections.
  ([#450](https://github.com/trinodb/trino-gateway/issues/450))
* Fix failures when clients use HTTP/2.
  ([#451](https://github.com/trinodb/trino-gateway/issues/451))
* Ensure that the user history dashboard displays the correct user name.
  ([#370](https://github.com/trinodb/trino-gateway/issues/370))
* Fix incorrect routing of OAuth logout requests.
  ([#455](https://github.com/trinodb/trino-gateway/pull/455))

More details and a list of all merged pull requests are [available in the 
milestone 11 list](https://github.com/trinodb/trino-gateway/pulls?q=is%3Apr+milestone%3A11+is%3Aclosed).

### Trino Gateway 10 (24 Jul 2024) { id="10" }

Artifacts:

* [JAR file gateway-ha-10-jar-with-dependencies.jar](https://repo1.maven.org/maven2/io/trino/gateway/gateway-ha/10/gateway-ha-10-jar-with-dependencies.jar)
* Container image `trinodb/trino-gateway:10`
* Source code as
  [tar.gz](https://github.com/trinodb/trino-gateway/archive/refs/tags/10.tar.gz)
  or [zip](https://github.com/trinodb/trino-gateway/archive/refs/tags/10.zip)
* Helm chart `10.0.0` in `helm/trino-gateway` of the tagged source code

Changes:

* [:warning: Breaking change:](#breaking) Remove support for Dropwizard and
  Jetty Proxy integration and usage. Add
  [Airlift](https://github.com/airlift/airlift) as the base application
  framework as
  used in Trino. This changes the supported Trino Gateway startup, configuration
  files, and relevant properties. Find details in the
  [documentation](quickstart.md), and specifically refer to the
  [upgrade guide](migration-to-airlift.md) when migrating from older releases.
  ([#41](https://github.com/trinodb/trino-gateway/issues/41))
* [:warning: Breaking change:](#breaking) Improve Helm chart reliability and
  adjust to new Airlift base framework.
  ([#401](https://github.com/trinodb/trino-gateway/pull/401))
* Enable routing rules to use query and user details extracted from the HTTP
  request.
  ([#325](https://github.com/trinodb/trino-gateway/pull/325))
* Add support for using an OIDC claim for authorization.
  ([#322](https://github.com/trinodb/trino-gateway/pull/322))
* Improve OIDC spec compliance, and add state and nonce verification.
  ([#348](https://github.com/trinodb/trino-gateway/pull/339))
* Allow null values for `userName` and `source` in the query history.
  ([#381](https://github.com/trinodb/trino-gateway/pull/381))
* Show times in query distribution graph in UI in local time instead of UTC.
  ([#369](https://github.com/trinodb/trino-gateway/pull/369))
* Fix problems with secrets, liveness, and readiness templates in Helm chart.
  ([#348](https://github.com/trinodb/trino-gateway/pull/348))
* Fix cluster reordering issue in the cluster user interface.
  ([#331](https://github.com/trinodb/trino-gateway/pull/331))
* Fix creation of new resource groups.
  ([#379](https://github.com/trinodb/trino-gateway/pull/379))

### Trino Gateway 9 (8 May 2024) { id="9" }

Artifacts:

* [JAR file gateway-ha-9-jar-with-dependencies.jar](https://repo1.maven.org/maven2/io/trino/gateway/gateway-ha/9/gateway-ha-9-jar-with-dependencies.jar)
* Container image `trinodb/trino-gateway:9`
* Source code as
  [tar.gz](https://github.com/trinodb/trino-gateway/archive/refs/tags/9.tar.gz)
  or [zip](https://github.com/trinodb/trino-gateway/archive/refs/tags/9.zip)

Changes:

* Ensure inclusion of UI in JAR and container artifacts. ([#337](https://github.com/trinodb/trino-gateway/pull/337))

### Trino Gateway 8 (6 May 2024) { id="8" }

Artifacts:

* [JAR file gateway-ha-8-jar-with-dependencies.jar](https://repo1.maven.org/maven2/io/trino/gateway/gateway-ha/8/gateway-ha-8-jar-with-dependencies.jar)
* Container image `trinodb/trino-gateway:8`
* Source code as
  [tar.gz](https://github.com/trinodb/trino-gateway/archive/refs/tags/8.tar.gz)
  or [zip](https://github.com/trinodb/trino-gateway/archive/refs/tags/8.zip)

Changes:

* Add support for configurable router policies. ([#98](https://github.com/trinodb/trino-gateway/pull/98))
* Add a router policy based on query count per cluster. ([#98](https://github.com/trinodb/trino-gateway/pull/98))
* Add a router policy for select paths based on cookie content. ([#188](https://github.com/trinodb/trino-gateway/pull/188))
* Support configuring access permissions for UI pages. ([#296](https://github.com/trinodb/trino-gateway/pull/296))
* Add Helm chart for Kubernetes deployments. ([#87](https://github.com/trinodb/trino-gateway/issues/87))
* Require Java 21 for build and runtime. ([#225](https://github.com/trinodb/trino-gateway/pull/225))
* Fix the `userInfo` resource to pass role information used by the API, so that
  the webapp authentication matches the API authentication. ([#310](https://github.com/trinodb/trino-gateway/pull/310))

### Trino Gateway 7 (21 Mar 2024) { id="7" }

Artifacts:

* [JAR file gateway-ha-7-jar-with-dependencies.jar](https://repo1.maven.org/maven2/io/trino/gateway/gateway-ha/7/gateway-ha-7-jar-with-dependencies.jar)
* Container image `trinodb/trino-gateway:7`
* Source code as
  [tar.gz](https://github.com/trinodb/trino-gateway/archive/refs/tags/7.tar.gz)
  or [zip](https://github.com/trinodb/trino-gateway/archive/refs/tags/7.zip)

Changes:

* Replace user interface with a new modern UI. ([#116](https://github.com/trinodb/trino-gateway/pull/116))
* Improve logging configurability. Users must update to the 
  [new logging configuration](./installation.md#logging). ([#260](https://github.com/trinodb/trino-gateway/pull/260))
* Improve Trino cluster health check performance and remove authentication requirement 
  by using the `v1/info` endpoint. ([#264](https://github.com/trinodb/trino-gateway/pull/264))
* Fix query id tracking based on request querystring parsing. ([#265](https://github.com/trinodb/trino-gateway/pull/265))

[Details about all pull requests and issues](https://github.com/trinodb/trino-gateway/issues?q=milestone%3A7+is%3Aclosed)

### Trino Gateway 6 (16 Feb 2024) { id="6" }

Artifacts:

* [JAR file gateway-ha-6-jar-with-dependencies.jar](https://repo1.maven.org/maven2/io/trino/gateway/gateway-ha/6/gateway-ha-6-jar-with-dependencies.jar)
* Container image `trinodb/trino-gateway:6`
* Source code as
  [tar.gz](https://github.com/trinodb/trino-gateway/archive/refs/tags/6.tar.gz)
  or [zip](https://github.com/trinodb/trino-gateway/archive/refs/tags/6.zip)

Changes:

* Add Docker container build, publishing, and usage setup and instructions. ([#86](https://github.com/trinodb/trino-gateway/issues/86))

[Details about all pull requests and issues](https://github.com/trinodb/trino-gateway/issues?q=milestone%3A6+is%3Aclosed)

### Trino Gateway 5 (24 Jan 2024) { id="5" }

Artifacts:

* [gateway-ha-5-jar-with-dependencies.jar](https://repo1.maven.org/maven2/io/trino/gateway/gateway-ha/5/gateway-ha-5-jar-with-dependencies.jar)
* Source code as
  [tar.gz](https://github.com/trinodb/trino-gateway/archive/refs/tags/5.tar.gz)
  or [zip](https://github.com/trinodb/trino-gateway/archive/refs/tags/5.zip)


Changes:

* Add support for OAuth 2 audience use. ([#164](https://github.com/trinodb/trino-gateway/pull/164))
* Add quickstart scripts and documentation. ([#110](https://github.com/trinodb/trino-gateway/pull/110))
* Add project logo. ([#111](https://github.com/trinodb/trino-gateway/pull/111))
* Prevent ignoring HTTP header configuration. ([#100](https://github.com/trinodb/trino-gateway/issues/100))

[Details about all merged pull requests](https://github.com/trinodb/trino-gateway/pull/168)

### Trino Gateway 4 (30 Nov 2023) { id="4" }

Artifacts:

* [gateway-ha-4-jar-with-dependencies.jar](https://repo1.maven.org/maven2/io/trino/gateway/gateway-ha/4/gateway-ha-4-jar-with-dependencies.jar)
* Source code as
  [tar.gz](https://github.com/trinodb/trino-gateway/archive/refs/tags/4.tar.gz)
  or [zip](https://github.com/trinodb/trino-gateway/archive/refs/tags/4.zip)

Changes:

* Add support for configuring additional whitelisted URL paths. ([#63](https://github.com/trinodb/trino-gateway/pull/63))
* Improve flexibility of user and group name matching for authorization. ([#32](https://github.com/trinodb/trino-gateway/pull/32))
* Add support to use external URL for backend creation. ([#76](https://github.com/trinodb/trino-gateway/pull/76))
* Enable configuration of HTTP header size. ([#67](https://github.com/trinodb/trino-gateway/pull/67))
* Automatically set JDBC configuration parameter for TLS when connecting to
  clusters for monitoring. ([#71](https://github.com/trinodb/trino-gateway/pull/71))
* Modernize application and remove potential for undiscovered bugs and security
  issues with update of many core dependencies. ([#59](https://github.com/trinodb/trino-gateway/pull/59))
* Avoid failure when unhandled OIDC properties are present. ([#69](https://github.com/trinodb/trino-gateway/pull/69))
* Prevent failures resulting from reloading and parsing rules file. ([#5](https://github.com/trinodb/trino-gateway/pull/5))

[Details about all merged pull requests](https://github.com/trinodb/trino-gateway/pull/73)

## 2023

### Trino Gateway 3 (26 Sep 2023) { id="3" }

Artifacts:

* [gateway-ha-3-jar-with-dependencies.jar](https://repo1.maven.org/maven2/io/trino/gateway/gateway-ha/3/gateway-ha-3-jar-with-dependencies.jar)
* Source code as
  [tar.gz](https://github.com/trinodb/trino-gateway/archive/refs/tags/3.tar.gz)
  or [zip](https://github.com/trinodb/trino-gateway/archive/refs/tags/3.zip)

Changes:

The first release of Trino Gateway is based on the 
[Presto Gateway](https://github.com/lyft/presto-gateway/) 1.9.5 codebase
([#4](https://github.com/trinodb/trino-gateway/pull/4)) with these additions:

* Add authentication and authorization with LDAP, OIDC and user list from config
  file. ([#9](https://github.com/trinodb/trino-gateway/pull/9))
* Add support for user, admin and API roles. ([#9](https://github.com/trinodb/trino-gateway/pull/9))
* Add healthcheck for Trino backends using JDBC. ([#9](https://github.com/trinodb/trino-gateway/pull/9))
* Add TCP check for routing. ([#9](https://github.com/trinodb/trino-gateway/pull/9))
* Add logic to route requests only to healthy backends. ([#13](https://github.com/trinodb/trino-gateway/pull/13))
* Add PostgreSQL support for backend database. ([#13](https://github.com/trinodb/trino-gateway/pull/13))
* Allow routing of `/v1/node` endpoint URL. ([#27](https://github.com/trinodb/trino-gateway/pull/27))
* Filter logs for sensitive information. ([#9](https://github.com/trinodb/trino-gateway/pull/9))
* Require Java 17 for build and runtime. ([#16](https://github.com/trinodb/trino-gateway/pull/16))
* Deactivate clusters with zero workers. ([#13](https://github.com/trinodb/trino-gateway/pull/13))
* Remove concurrency issue from repeated rules file loading. ([#9](https://github.com/trinodb/trino-gateway/pull/9))

[Details about all merged pull requests](https://github.com/trinodb/trino-gateway/pull/52)

## Breaking changes <a name="breaking">

Starting with Trino Gateway 10, release note entries include a [:warning:
Breaking change:](#breaking) prefix to highlight any changes as potentially 
breaking changes. The following changes are considered and may require
adjustments:

* Removal or renaming of configuration properties that may prevent startup or 
  require configuration changes.
* Changes to default values for configuration properties that may significantly
  change the behavior of a system.
* Updates to the requirements for external systems or software used with 
  Trino Gateway.
* Non-backwards compatible changes which may require router modules to 
  be updated.
* Otherwise significant changes that requires specific attention from teams 
  managing a Trino Gateway deployment.
