# Development

## Build requirements

* Mac OS X or Linux
* Java 25+, 64-bit
* Docker

#### Running Trino Gateway in your IDE

The best way to run Trino Gateway for development is to run the
`TrinoGatewayRunner` class.
You need to run `io.trino.gateway.TrinoGatewayRunner.main()` method on your IDE
or execute the following command:

```shell
./mvnw test-compile exec:java -pl gateway-ha -Dexec.classpathScope=test -Dexec.mainClass="io.trino.gateway.TrinoGatewayRunner"
```

### Build and run

#### Locally

This project requires Java 25. Note that higher version of Java have not been
verified and may run into unexpected issues.

Run `./mvnw clean install` to build `trino-gateway`. VM options required for
compilation and testing are specified in `.mvn/jvm.config`.

Edit the configuration file `config.yaml` in the `gateway-ha` folder
and update the mysql db information.

Note that tests using Oracle are disabled by default on non-x86_64 CPU architectures.
To enable them, set the environment variable `TG_RUN_ORACLE_TESTS=true`. These tests
will always be run in GitHub CI.

```shell
cd gateway-ha/target/
java -jar gateway-ha-{{VERSION}}-jar-with-dependencies.jar ../config.yaml
```

#### In Docker

Follow the separate instructions for building the container and running Trino
Gateway with docker compose from the `README.md` file in the `docker` folder.

## Contact, help, and issues

You can contact the Trino Gateway users and contributors on
[Trino slack](https://trino.io/slack) in the `#trino-gateway` and
`#trino-gateway-dev` channels. Use these channels for questions and discussion
about Trino Gateway installation, usage, and development.

If you encounter specific issues or want to propose new features,
[file an issue](https://github.com/trinodb/trino-gateway/issues) and follow the
contribution process for next steps.

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

## Contributor meetings

Contributor meetings are open to anyone and held every two weeks. [Meeting
notes and other details are available on GitHub](https://github.com/trinodb/trino-gateway/wiki/Contributor-meetings).

## Release process

A full release process consists a number of steps.

1. Create a release notes pull request including the following changes:
    - Add new release notes in `docs/release-notes.md`.
    - Update `VERSION` in `docs/quickstart.md`. 
    
    See the [example pull request for Trino Gateway
  17](https://github.com/trinodb/trino-gateway/pull/792) for more details.

2. In parallel prepare a pull request to update the Trino Gateway Helm chart in
   the [charts repository](https://github.com/trinodb/charts):
    - Update `appVersion` to new version and `version` to the new version
      `1.x.0`, where `x` is the Trino Gateway version `charts/gateway/Chart.yaml`. For example, update to `appVersion: "17"` and `version: "1.17.0"`.
    - Update the links `charts/gateway/README.md`
    - Update the version table in `README.md`
    
    See the [example pull request for the 1.17.0 chart](https://github.com/trinodb/charts/pull/386) for more details.

3. Organize review and approval for the release notes PR.

4. Confirm that the team wants to proceed with a release via discussions in the
   developer syncs and the slack channel.

5. Confirm that the main branch builds are successful.

6. Merge the release notes pull request.

7. After the release notes PR is merged, kick off [the release
   workflow](https://github.com/trinodb/trino-gateway/actions/workflows/release.yml)
   to deploy the binaries to Maven Central. A successful release build performs
   the necessary commits, and pushes the binaries to Maven Central.

8. Wait until the workflow completes and until [the binaries available on Maven
   Central](https://repo1.maven.org/maven2/io/trino/gateway/gateway-ha/). A
   folder with the new version should be visible and the link in the release to
   the JAR must work.

9. Once the binaries are on Maven Central you can kick off the [release workflow
   for publishing the container image to Docker
   Hub](https://github.com/trinodb/trino-gateway/actions/workflows/release-docker.yml).

10. With the deployment completed, you can verify the container by pulling it
    down manually, for example with:

    ```shell
    docker pull trinodb/trino-gateway:17
    ```
    
    You can also verify availability at
    [https://hub.docker.com/r/trinodb/trino-gateway](https://hub.docker.com/r/trinodb/trino-gateway).

11. Push to the pull request in the charts repository and confirm that the build
    is now successful, since it can finally pull the referenced container image.

12. Once the PR in the charts repository completed, request review and approval
    and merge the PR to publish the Helm chart.

13. Announce the release on Trino Slack and LinkedIn.

14. Go back to reviewing PRs, writing code, and get ready for another release
    preparation.
