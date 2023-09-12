**trino-gateway documentation**

<table>
  <tr>
    <td><a href="design.md">Design</a></td>
    <td><b><a href="development.md">Development</a></b></td>
    <td><a href="security.md">Security</a></td>
    <td><a href="operation.md">Operation</a></td>
    <td><a href="gateway-api.md">Gateway API</a></td>
    <td><a href="resource-groups-api.md">Resource groups API</a></td>
    <td><a href="routing-rules.md">Routing rules</a></td>
    <td><a href="references.md">References</a></td>
  </tr>
</table>

# Development

## How to setup a dev environment

Step 1: setup mysql. Install docker with docker-compose and run the below
command when setting up first time:

#### Run the services - mysqldb, two instances of trino

- This setup helps you develop and test any routing rules for the trino
- Both trino services would have a single `system` catalog
- Add the catalog properties files in ` bin/localdev/coordinator/` for
  additional catalogs

```
cd localdev
docker-compose up -d
```

#### Check the "Status' of the services by

`docker-compose ps`

#### Create the schema for the backends, once mysqldb becomes healthy

`docker-compose exec mysqldb sh -c "mysql -uroot -proot123 -hmysqldb -Dtrinogateway < /etc/mysql/gateway-ha-persistence.sql"`

#### Add the backends for mysqldb

`docker-compose exec mysqldb sh -c "mysql -uroot -proot123 -hmysqldb -Dtrinogateway < /etc/mysql/add_backends.sql"`

#### Create the schema for the backends, once postgres becomes healthy

`docker-compose exec postgres sh -c 'PGPASSWORD="P0stG&es" psql -h localhost -p 5432 -U trino_gateway_db_admin -d trino_gateway_db -f /etc/postgresql/gateway-ha-persistence-postgres.sql'`

#### Add the backends for postgres

`docker-compose exec postgres sh -c 'PGPASSWORD="P0stG&es" psql -h localhost -p 5432 -U trino_gateway_db_admin -d trino_gateway_db -f /etc/postgresql/add_backends_postgres.sql'`

It would add 2 trino backend records which can be used for the development and
testing

### Build and run

This project requires Java 17. Note that higher version of Java have not been
verified and may run into unexpected issues.

Run `./mvnw clean install` to build `trino-gateway`. VM options required for
compilation and testing are specified in `.mvn/jvm.config`.

Edit the [config file](/gateway-ha/gateway-ha-config.yml) and update the mysql
db information.

```
cd gateway-ha/target/
java --add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.net=ALL-UNNAMED -jar gateway-ha-{{VERSION}}-jar-with-dependencies.jar server ../gateway-ha-config.yml
```

If you encounter a `Failed to connect to JDBC URL` error with the MySQL backend,
this may be due to newer versions of Java disabling certain algorithms when
using SSL/TLS, in particular `TLSv1` and `TLSv1.1`. This causes `Bad handshake`
errors when connecting to the MySQL server. You can avoid this by enabling
`TLSv1` and `TLSv1.1` in your JDK, or by adding `sslMode=DISABLED` to your
connection string.

To enable TLS1 and 1.1, in

```
${JAVA_HOME}/jre/lib/security/java.security
```

search for `jdk.tls.disabledAlgorithms`, it should look something like this:

```
jdk.tls.disabledAlgorithms=SSLv3, TLSv1, TLSv1.1, RC4, DES, MD5withRSA, \
    DH keySize < 1024, EC keySize < 224, 3DES_EDE_CBC, anon, NULL, \
    include jdk.disabled.namedCurves
```

Remove `TLSv1, TLSv1.1` and redo the above steps to build and run
`trino-gateway`.

If you see test failures while building `trino-gateway` or in an IDE, please run
`mvn process-classes` to instrument javalite models which are used by the tests.
Refer to the
[javalite-examples](https://github.com/javalite/javalite-examples/tree/master/simple-example#instrumentation)
for more details.

## Contributing

Want to help build Trino Gateway? Check out our [contributing
documentation](../.github/CONTRIBUTING.md)
