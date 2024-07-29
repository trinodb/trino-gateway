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
java -jar gateway-ha-{{VERSION}}-jar-with-dependencies.jar ../gateway-ha-config.yml
```

#### In Docker

Follow the separate instructions for building the container and running Trino
Gateway with docker compose from the `README.md` file in the `docker` folder.

## Contributing

Want to help build Trino Gateway? Check out our [contributing
documentation](https://github.com/trinodb/trino-gateway/blob/main/.github/CONTRIBUTING.md)

## Maintainers

The following Trino and Trino Gateway maintainers are involved in Trino
Gateway, and can help with pull request reviews and merges.

* [:fontawesome-brands-github: chaho12 - Jaeho Yoo](https://github.com/chaho12)
* [:fontawesome-brands-github: ebyhr - Yuya Ebihara](https://github.com/ebyhr)
* [:fontawesome-brands-github: mosabua - Manfred Moser](https://github.com/mosabua)
* [:fontawesome-brands-github: oneonestar - Star Poon](https://github.com/oneonestar)
* [:fontawesome-brands-github: vishalya - Vishal Jadhav](https://github.com/vishalya)
* [:fontawesome-brands-github: wendigo - Mateusz Gajewski](https://github.com/wendigo)
* [:fontawesome-brands-github: willmostly - Will Morrison](https://github.com/willmostly)

## Contributor meetings

Contributor meetings are open to anyone and held every two weeks. [Meeting
notes and other details are available on GitHub](https://github.com/trinodb/trino-gateway/wiki/Contributor-meetings).

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
