# Release notes

## Trino Gateway 6 (16 Feb 2024)

[gateway-ha-6-jar-with-dependencies.jar](https://repo1.maven.org/maven2/io/trino/gateway/gateway-ha/6/gateway-ha-6-jar-with-dependencies.jar)

* Add Docker container build, publishing, and usage setup and instructions. ([#86](https://github.com/trinodb/trino-gateway/issues/86))

[Details about all pull requests and issues](https://github.com/trinodb/trino-gateway/issues?q=milestone%3A6+is%3Aclosed)


## Trino Gateway 5 (24 Jan 2024)

[gateway-ha-5-jar-with-dependencies.jar](https://repo1.maven.org/maven2/io/trino/gateway/gateway-ha/5/gateway-ha-5-jar-with-dependencies.jar)

* Add support for OAuth 2 audience use. ([#164](https://github.com/trinodb/trino-gateway/pull/164))
* Add quickstart scripts and documentation. ([#110](https://github.com/trinodb/trino-gateway/pull/110))
* Add project logo. ([#111](https://github.com/trinodb/trino-gateway/pull/111))
* Prevent ignoring HTTP header configuration. ([#100](https://github.com/trinodb/trino-gateway/issue/100))

[Details about all merged pull requests](https://github.com/trinodb/trino-gateway/pull/168)

## Trino Gateway 4 (30 Nov 2023)

[gateway-ha-4-jar-with-dependencies.jar](https://repo1.maven.org/maven2/io/trino/gateway/gateway-ha/4/gateway-ha-4-jar-with-dependencies.jar)

* Add support for configuring additional whitelisted URL paths.([#63](https://github.com/trinodb/trino-gateway/pull/63))
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

## Trino Gateway 3 (26 Sep 2023)

[gateway-ha-3-jar-with-dependencies.jar](https://repo1.maven.org/maven2/io/trino/gateway/gateway-ha/3/gateway-ha-3-jar-with-dependencies.jar)

The first release of Trino Gateway is based on the [Presto
Gateway](https://github.com/lyft/presto-gateway/) 1.9.5 codebase
[#4](#4), with these additions:

* Add authentication and authorization with LDAP, OIDC and user list from config
  file. ([#9](https://github.com/trinodb/trino-gateway/pull/9))
* Add support for user, admin and API roles. ([#9](https://github.com/trinodb/trino-gateway/pull/9))
* Add healthcheck for Trino backends using JDBC. ([#9](https://github.com/trinodb/trino-gateway/pull/9))
* Add TCP check for routing. ([#9](https://github.com/trinodb/trino-gateway/pull/9))
* Add logic to route requests only to healthy backends. ([#13](https://github.com/trinodb/trino-gateway/pull/13))
* Add PostgreSQL support for backend database. ([#13](https://github.com/trinodb/trino-gateway/pull/13))
* Allow routing of `/v1/node` endpoint URL. ([#27](https://github.com/trinodb/trino-gateway/pull/27))
* Filter logs for sensitive information. ([#9](https://github.com/trinodb/trino-gateway/pull/9))
* Require Java 17 for build and runtime. ([#14](https://github.com/trinodb/trino-gateway/pull/16))
* Deactivate clusters with zero workers. ([#13](https://github.com/trinodb/trino-gateway/pull/13))
* Remove concurrency issue from repeated rules file loading. ([#9](https://github.com/trinodb/trino-gateway/pull/9))

[Details about all merged pull requests](https://github.com/trinodb/trino-gateway/pull/52)
