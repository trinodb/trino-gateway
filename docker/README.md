# Trino Gateway Docker image

The Docker image of Trino Gateway is designed for the following use cases:

* Manual usage in front of Trino clusters by mounting in a configuration file
* Automated usage with an orchestration system like Kubernetes to simplify
  deployment

## Build requirements

This docker build process requires the following software:

* [Docker Compose V2](https://docs.docker.com/compose/)
* jq

# Building a custom Docker image

Use the following steps to build a Docker image from your local Trino Gateway
codebase

First, run the [Maven build in the project root](../docs/development.md).

Then build the image for your desired processor architecture in the `docker` directory:

```bash
./build.sh -a arm64
```

By default, the scripts builds all valid processor architectures `amd64`,
`arm64`, and `ppc64le`:

```bash
./build.sh
```

The Docker build process prints the ID of the built image. It also tags the
image with `trino-gateway:xxx-SNAPSHOT-yyy`, where `xxx-SNAPSHOT` is the version
number and`-yyy` is the processor architecture:

```bash
$ docker images
REPOSITORY                     TAG                  IMAGE ID       CREATED          SIZE
trino-gateway                  6-SNAPSHOT-ppc64le   a72b750d2745   33 seconds ago   547MB
trino-gateway                  6-SNAPSHOT-arm64     bc5e8b0db63c   35 seconds ago   523MB
trino-gateway                  6-SNAPSHOT-amd64     6c066fa5b0c5   36 seconds ago   518MB
...
```

To build an image for a specific, already released version of Trino Gateway, use
the `-r` option. The build script downloads all the required artifacts:

```bash
./build.sh -r 4
```

Set the environment variable `TRINO_GATEWAY_BASE_IMAGE` to use a specific base image
to build Trino Gateway image.

```bash
export TRINO_GATEWAY_BASE_IMAGE=<image>
./build.sh
```

Use the `-h` option for further help.

### Run Trino Gateway

You can launch Trino Gateway and required PostgreSQL for testing purposes with
the following command examples using `docker compose`.

Use a locally-built image on a ARM-based machine, such as a Macbook laptop.

```shell
export TRINO_GATEWAY_IMAGE="trino-gateway:6-SNAPSHOT-arm64"
```

Use a locally-built image on a AMD64-based machine, such as a typical Windows
or Linux desktop or laptop.

```shell
export TRINO_GATEWAY_IMAGE="trino-gateway:6-SNAPSHOT-amd64"
```

Use a published image from Docker Hub.

```shell
export TRINO_GATEWAY_IMAGE="trinodb/trino-gateway:latest"
```

The release process publishes images for Trino Gateway 6 and newer to Docker Hub.

Next set the image and platform:

Start Trino Gateway and its PostgreSQL backend database, and wait until the 
health check is successful:

```bash
docker compose up --wait
```

Inspect the logs for progress and troubleshooting:

```bash
docker compose logs trino-gateway
```

Typically your operating system automatically sets the default Docker platform
value. In some cases it can be useful to explicitly set it.

For example, on ARM64-based MacOS you can set it to use `linux` because it 
otherwise is potentially set to `darwin` and there are no PostgreSQL images 
available for `darwin` and this can prevent starting the Trino Gateway with
docker compose.

```shell
export DOCKER_DEFAULT_PLATFORM="linux/arm64"
```

You can also set platform without operating system identifier:

```shell 
export DOCKER_DEFAULT_PLATFORM="amd64"
```

### Running

Once everything is up and running, you can use the REST API to show the
configured backends:

```bash
curl localhost:8080/api/public/backends
```

The Trino Gateway is available at [http://localhost:8080](http://localhost:8080).

The PostgreSQL backend database for Trino Gateway runs on `localhost:5432`. You
can query it for troubleshooting and other purposes using the credentials and
details found in the `postgres-backend-compose.yml` file.

## Configuration

The image uses the configuration file `gateway-ha/gateway-ha-config-docker.yml`
from the project checkout, and mounts it at `/opt/trino/gateway-ha-config.yml`.

## Health check

By default the container health check uses the file `docker/bin/health-check`
mounted at `/usr/lib/trino/bin/health-check`. The scripts expects a 2XX response
from the server at `/api/public/backends`.
