# Development

## Build requirements

* Mac OS X or Linux
* Java 21+, 64-bit
* Docker

#### Running Trino Gateway in your IDE

The best way to run Trino Gateway for development is to run the
`TrinoGatewayRunner` class.
You need to run `io.trino.gateway.TrinoGatewayRunner.main()` method on your IDE
or execute the following command:

```sh
./mvnw test-compile exec:java -pl gateway-ha -Dexec.classpathScope=test -Dexec.mainClass="io.trino.gateway.TrinoGatewayRunner"
```

### Build and run

#### Locally

This project requires Java 21. Note that higher version of Java have not been
verified and may run into unexpected issues.

Run `./mvnw clean install` to build `trino-gateway`. VM options required for
compilation and testing are specified in `.mvn/jvm.config`.

Edit the configuration file `gateway-ha-config.yml` in the `gateway-ha` folder
and update the mysql db information.

```
cd gateway-ha/target/
java --add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.net=ALL-UNNAMED -jar gateway-ha-{{VERSION}}-jar-with-dependencies.jar server ../gateway-ha-config.yml
```

#### In Docker

Follow the separate instructions for building the container and running Trino
Gateway with docker compose from the `README.md` file in the `docker` folder.

#### Common Run Failures

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
documentation](https://github.com/trinodb/trino-gateway/blob/main/.github/CONTRIBUTING.md)

## Release process

A full release process consists of the following steps:

Merge the pull request for the release notes and pull the changes locally:

```shell
cd trino-gateway
git checkout main
git pull
```

Run a Maven release build:

```shell
./mvnw clean release:prepare release:perform
```

A successful release build performs the necessary commits, and pushes the
binaries to Maven Central staging.

Close and release the staging repository, and wait until the sync to Central is
completed. Confirm the presence of the artifacts at
[https://repo.maven.apache.org/maven2/io/trino/gateway/gateway-ha/](https://repo.maven.apache.org/maven2/io/trino/gateway/gateway-ha/).

Ensure that you are logged into Docker Hub  with suitable permissions, and run
the container release script with the version  number that was just released, 
for example `6`:

```shell
docker/release-docker.sh 6
```

Once completed, verify the availability at
[https://hub.docker.com/r/trinodb/trino-gateway](https://hub.docker.com/r/trinodb/trino-gateway).

Announce the release on Trino Slack and LinkedIn.
