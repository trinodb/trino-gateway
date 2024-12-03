# Release notes

## Trino Gateway 13 (3 Dec 2024) { id="13" }

Artifacts:

* [JAR file gateway-ha-13-jar-with-dependencies.jar](https://repo1.maven.org/maven2/io/trino/gateway/gateway-ha/13/gateway-ha-13-jar-with-dependencies.jar)
* Container image `trinodb/trino-gateway:13`
* Source code as
  [tar.gz](https://github.com/trinodb/trino-gateway/archive/refs/tags/13.tar.gz)
  or [zip](https://github.com/trinodb/trino-gateway/archive/refs/tags/13.zip)
* Helm chart `1.13.0` in `helm/trino-gateway` of the tagged source code

Changes:

* Allow proxying of HTTP PUT requests to Trino clusters.
  ([#543](https://github.com/trinodb/trino-gateway/pull/543))
* Add a filter for `source` to the **History** page in the UI.
  ([#551](https://github.com/trinodb/trino-gateway/pull/551))
* Log out inactive users from the UI automatically.
  ([#544](https://github.com/trinodb/trino-gateway/pull/544))

## Trino Gateway 12 (7 Nov 2024) { id="12" }

Artifacts:

* [JAR file gateway-ha-12-jar-with-dependencies.jar](https://repo1.maven.org/maven2/io/trino/gateway/gateway-ha/12/gateway-ha-12-jar-with-dependencies.jar)
* Container image `trinodb/trino-gateway:12`
* Source code as
  [tar.gz](https://github.com/trinodb/trino-gateway/archive/refs/tags/12.tar.gz)
  or [zip](https://github.com/trinodb/trino-gateway/archive/refs/tags/12.zip)
* Helm chart `0.12.0` in [the charts repository](https://github.com/trinodb/charts)

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

## Trino Gateway 11 (12 Sep 2024) { id="11" }

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

## Trino Gateway 10 (24 Jul 2024) { id="10" }

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

## Trino Gateway 9 (8 May 2024) { id="9" }

Artifacts:

* [JAR file gateway-ha-9-jar-with-dependencies.jar](https://repo1.maven.org/maven2/io/trino/gateway/gateway-ha/9/gateway-ha-9-jar-with-dependencies.jar)
* Container image `trinodb/trino-gateway:9`
* Source code as
  [tar.gz](https://github.com/trinodb/trino-gateway/archive/refs/tags/9.tar.gz)
  or [zip](https://github.com/trinodb/trino-gateway/archive/refs/tags/9.zip)

Changes:

* Ensure inclusion of UI in JAR and container artifacts. ([#337](https://github.com/trinodb/trino-gateway/pull/337))

## Trino Gateway 8 (6 May 2024) { id="8" }

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

## Trino Gateway 7 (21 Mar 2024) { id="7" }

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

## Trino Gateway 6 (16 Feb 2024) { id="6" }

Artifacts:

* [JAR file gateway-ha-6-jar-with-dependencies.jar](https://repo1.maven.org/maven2/io/trino/gateway/gateway-ha/6/gateway-ha-6-jar-with-dependencies.jar)
* Container image `trinodb/trino-gateway:6`
* Source code as
  [tar.gz](https://github.com/trinodb/trino-gateway/archive/refs/tags/6.tar.gz)
  or [zip](https://github.com/trinodb/trino-gateway/archive/refs/tags/6.zip)

Changes:

* Add Docker container build, publishing, and usage setup and instructions. ([#86](https://github.com/trinodb/trino-gateway/issues/86))

[Details about all pull requests and issues](https://github.com/trinodb/trino-gateway/issues?q=milestone%3A6+is%3Aclosed)

## Trino Gateway 5 (24 Jan 2024) { id="5" }

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

## Trino Gateway 4 (30 Nov 2023) { id="4" }

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

## Trino Gateway 3 (26 Sep 2023) { id="3" }

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
