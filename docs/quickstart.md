# Quickstart

The scripts from this quickstart guide set up a local environment consisting of
two Trino servers and a PostgreSQL database running in Docker, and a Trino
Gateway server running in the host operating system. 

## Start Trino Gateway server

The following script starts a Trino Gateway server using the 
[Quickstart configuration](config.yaml) at http://localhost:8080.
It also starts a dockerized PostgreSQL database at localhost:5432.

To start the server, copy the script below to a temporary directory 
under the project root folder, and run it at the temporary directory.

It  copies the following, necessary files to current directory:

- `trino-gateway-server.tar.gz` from Maven Central using the version specified in the script
- `config.yaml` from the `docs` folder of the current project folder to etc directory of the Trino Gateway server

```shell
#!/usr/bin/env sh

VERSION=16
BASE_URL="https://repo1.maven.org/maven2/io/trino/gateway/trino-gateway-server"
GATEWAY_TAR="trino-gateway-server-$VERSION.tar.gz"
GATEWAY_DIR="trino-gateway-server-$VERSION"
CONFIG_YAML="config.yaml"

# Copy necessary files
copy_and_extract_files() {
    if [[ ! -f "$GATEWAY_TAR" ]]; then
        echo "Fetching $GATEWAY_TAR"
        curl -O "$BASE_URL/$VERSION/$GATEWAY_TAR"
    fi

    tar -xzf "$GATEWAY_TAR"
    mkdir -p "$GATEWAY_DIR"/etc
    echo -e "-server\n-XX:MinRAMPercentage=80\n-XX:MaxRAMPercentage=80" > "$GATEWAY_DIR"/etc/jvm.config

    [[ ! -f "$CONFIG_YAML" ]] && cp docs/$CONFIG_YAML .
    cp $CONFIG_YAML "$GATEWAY_DIR"/etc/
}

# Start PostgreSQL database if not running
start_postgres_db() {
    if ! docker ps --format '{{.Names}}' | grep -q '^local-postgres$'; then
        echo "Starting PostgreSQL database container"
        PGPASSWORD=mysecretpassword
        docker run -v "$PWD/$POSTGRES_SQL:/tmp/$POSTGRES_SQL" \
            --name local-postgres -p 5432:5432 -e POSTGRES_PASSWORD=$PGPASSWORD -d postgres
        sleep 5
        docker exec local-postgres psql -U postgres -h localhost -c 'CREATE DATABASE gateway'
    fi
}

# Main execution flow
copy_and_extract_files
start_postgres_db

# Start Trino Gateway server
echo "Starting Trino Gateway server..."
./$GATEWAY_DIR/bin/launcher start --config=$GATEWAY_DIR/etc/$CONFIG_YAML
```

You can clean up by running

```shell
docker kill local-postgres && docker rm local-postgres
kill -5 $(jps | grep HaGatewayLauncher | cut -d' ' -f1)
```

## Add Trino backends

This following script starts two dockerized Trino servers at 
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
    curl -H "Content-Type: application/json" -X POST \
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
