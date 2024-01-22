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

# Development

## Build requirements

* Mac OS X or Linux
* Java 17+, 64-bit
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

#### In Docker

From the root of the directory run the following commands to build the jar, docker image, and then spin it up:

```
./mvnw clean install

# Note - Feel free to change the architecture and version being targeted
# Without specifying a release version with '-r' it'll default to the current snapshot
PLATFORM="amd64"
bash docker/build.sh -a ${PLATFORM}

# This grabs the version from the pom but you can manually specify it instead
TRINO_GATEWAY_VERSION=$("./mvnw" -f "pom.xml" --quiet help:evaluate -Dexpression=project.version -DforceStdout)
TRINO_GATEWAY_IMAGE="trino-gateway:${TRINO_GATEWAY_VERSION}-${PLATFORM}" docker compose -f docker/minimal-compose.yml up -d
```

The [config file found here](/gateway-ha/gateway-ha-config-docker.yml) is mounted into the container.

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
documentation](../.github/CONTRIBUTING.md)
