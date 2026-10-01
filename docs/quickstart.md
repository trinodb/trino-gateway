# Quickstart

The scripts from this quickstart guide set up a local environment consisting of
two Trino servers and a PostgreSQL database running in Docker, and a Trino
Gateway server running in the host operating system.

## Start Trino Gateway server

The following script starts a Trino Gateway server using the
[Quickstart configuration](config.yaml) at http://localhost:8080.
It also starts a dockerized PostgreSQL database at localhost:5432. Set the
`TRINO_GATEWAY_POSTGRES_IMAGE` environment variable to use a different
PostgreSQL container image.

To start the server, copy the script below to a temporary directory
under the project root folder, and run it at the temporary directory.

It copies the following, necessary files to current directory:

- `gateway-ha.jar` from Maven Central using the version specified in the script
- `config.yaml` from the `docs` folder of the current project folder

```shell
#!/usr/bin/env sh

VERSION=22
BASE_URL="https://repo1.maven.org/maven2/io/trino/gateway/gateway-ha"
JAR_FILE="gateway-ha-$VERSION-jar-with-dependencies.jar"
GATEWAY_JAR="gateway-ha.jar"
CONFIG_YAML="config.yaml"
POSTGRES_IMAGE="${TRINO_GATEWAY_POSTGRES_IMAGE:-postgres:17-alpine}"

# Copy necessary files
copy_files() {
    if [ ! -f "$GATEWAY_JAR" ]; then
        echo "Fetching $GATEWAY_JAR version $VERSION"
        curl -O "$BASE_URL/$VERSION/$JAR_FILE"
        mv "$JAR_FILE" "$GATEWAY_JAR"
    fi

    if [ ! -f "$CONFIG_YAML" ]; then
        cp ../docs/$CONFIG_YAML .
    fi
}

# Start PostgreSQL database if not running
start_postgres_db() {
    if docker ps --format '{{.Names}}' | grep -q '^local-postgres$'; then
        return
    fi
    if docker ps -a --format '{{.Names}}' | grep -q '^local-postgres$'; then
        echo "Starting existing PostgreSQL database container"
        docker start local-postgres > /dev/null || exit 1
    else
        echo "Starting PostgreSQL database container"
        docker run --name local-postgres -p 5432:5432 \
            -e POSTGRES_PASSWORD=mysecretpassword -e POSTGRES_DB=gateway \
            -d "$POSTGRES_IMAGE" || exit 1
    fi
    tries=0
    until docker exec local-postgres pg_isready -h localhost -U postgres -d gateway > /dev/null 2>&1; do
        tries=$((tries + 1))
        if [ "$tries" -ge 30 ]; then
            echo "PostgreSQL database is not ready after 30 seconds"
            exit 1
        fi
        sleep 1
    done
}

# Main execution flow
copy_files
start_postgres_db

# Start Trino Gateway server
echo "Starting Trino Gateway server..."
java -Xmx1g -jar ./$GATEWAY_JAR ./$CONFIG_YAML
```

You can clean up by running

```shell
docker kill local-postgres && docker rm local-postgres
kill $(jps | grep gateway-ha.jar | cut -d' ' -f1)
```

## Add Trino backends

The following script starts two dockerized Trino servers at
http://localhost:8081 and http://localhost:8082. It then adds them as backends
to the Trino Gateway server started by the preceding script.

```shell
#!/usr/bin/env sh

TRINO_IMAGE="trinodb/trino"
JAVA_OPTS="-Dhttp-server.process-forwarded=true"

# Start Trino servers
for i in 1 2; do
    PORT=808$i
    if ! lsof -i:$PORT > /dev/null; then
        docker run --name trino$i -d -p $PORT:8080 \
            -e JAVA_TOOL_OPTIONS="$JAVA_OPTS" $TRINO_IMAGE
    else
        echo "Warning: Port $PORT is already in use. Skipping trino$i."
    fi
done

# Add Trino servers as Gateway backends
add_backend() {
    curl -sS -H "Content-Type: application/json" -X POST \
        localhost:8080/gateway/backend/modify/add \
        -d "{
              \"name\": \"$1\",
              \"proxyTo\": \"http://localhost:808$2\",
              \"active\": true,
              \"routingGroup\": \"adhoc\"
            }"
}

# Adding Trino servers as backends
for i in 1 2; do
    add_backend "trino$i" "$i"
done
```

You can clean up by running

```shell
docker kill trino1 && docker rm trino1
docker kill trino2 && docker rm trino2
```
