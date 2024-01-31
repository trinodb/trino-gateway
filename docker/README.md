# Trino Gateway Docker Image

## About the Container

This Docker image is designed to:

* be spun up in front of Trino clusters by mounting in a configuration file
* simplify deployment into an orchestration system

## Quickstart

### Dependencies

This docker build process requires:

* [Docker Compose V2](https://docs.docker.com/compose/)
* jq

### Run the Trino Gateway server

You can launch the Trino Gateway and relevant dependencies through docker for testing purposes.

```bash
# Replace these variables to match your test requirements
# Ex: You may be locally building the docker image so something like
# `trino-gateway:5-SNAPSHOT-amd64` might be what you're expecting
TEST_GATEWAY_IMAGE="trinodb/trino-gateway:latest"
TEST_PLATFORM="amd64"

TRINO_GATEWAY_IMAGE=${TEST_GATEWAY_IMAGE} DOCKER_DEFAULT_PLATFORM=${TEST_PLATFORM} \
    docker compose -f minimal-compose.yml \
    up --wait
```

This will wait until docker has spun up the services and the gateway is healthy.
If the service doesn't come up successfully you can attempt to debug it by pulling the logs:
```
docker compose -f minimal-compose.yml logs gateway
```

The Trino Gateway server is now running on `localhost:8080` (the default port).

### Verify it Runs

Now that the gateway is up and running, here's a sample query that shows the backends configured:
```bash
curl localhost:8080/api/public/backends
```

Or, visit it in your browser by opening http://localhost:8080

## Configuration

Configuration is expected to be mounted to the exact path of `/opt/trino/gateway-ha-config.yml`.
If it is not mounted then the gateway will fail to initialize.

## Health Checking

By default the container health checking is done by the [/usr/lib/trino/bin/health-check](./bin/health-check)
script which simply expects a 2XX response from the server at `/api/public/backends`.

## Building a custom Docker image

To build an image for a locally modified version of Trino Gateway, run the Maven
build as normal for the `gateway-ha` modules, then build the image:

```bash
./build.sh
```

The Docker build process will print the ID of the image, which will also
be tagged with `trino-gateway:xxx-SNAPSHOT-yyy`, where `xxx-SNAPSHOT` is the version
number of the Trino Maven build and `-yyy` is the platform the image was built for.

To build an image for a specific released version of Trino Gateway,
specify the `-r` option, and the build script will download
all the required artifacts:

```bash
./build.sh -r 4
```

Set the environment variable `TRINO_GATEWAY_BASE_IMAGE` to use a specific base image
to build Trino Gateway image.

```bash
export TRINO_GATEWAY_BASE_IMAGE=<image>
./build.sh
```

## Getting Help

Join the Trino community [Slack](https://trino.io/slack.html).
