# Quickstart

The scripts from this quickstart guide set up a local environment consisting of
two Trino servers and a PostgreSQL database running in Docker, and a Trino
Gateway server running in the host operating system.

## Start Trino Gateway server

The following script starts a Trino Gateway server using the
[Quickstart configuration](quickstart-config.yaml) at http://localhost:8080.
It also starts a dockerized PostgreSQL database at localhost:5432.

To start the server, copy the script below to a temporary directory
under the project root folder, and run it at the temporary directory.

It  copies the following, necessary files to current directory:

- gateway-ha.jar
- gateway-ha-persistence-postgres.sql
- quickstart-config.yaml

```shell
#!/usr/bin/env sh

VERSION=13
BASE_URL="https://repo1.maven.org/maven2/io/trino/gateway/gateway-ha"
POSTGRES_SQL="gateway-ha-persistence-postgres.sql"
JAR_FILE="gateway-ha-$VERSION-jar-with-dependencies.jar"
GATEWAY_JAR="gateway-ha.jar"
CONFIG_YAML="quickstart-config.yaml"

# Copy necessary files
copy_files() {
    if [[ ! -f "$GATEWAY_JAR" ]]; then
        echo "Fetching $GATEWAY_JAR version $VERSION"
        curl -O "$BASE_URL/$VERSION/$JAR_FILE"
        mv "$JAR_FILE" "$GATEWAY_JAR"
    fi

    [[ ! -f "$CONFIG_YAML" ]] && cp ../docs/$CONFIG_YAML .
    [[ ! -f "$POSTGRES_SQL" ]] && cp ../gateway-ha/src/main/resources/$POSTGRES_SQL .
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
        docker exec local-postgres psql -U postgres -h localhost -d gateway -f /tmp/$POSTGRES_SQL
    fi
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
kill -5 $(jps | grep gateway-ha.jar | cut -d' ' -f1)
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
    docker run --name trino$i -d -p 808$i:8080 \
        -e JAVA_TOOL_OPTIONS="$JAVA_OPTS" $TRINO_IMAGE
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

for i in 1 2; do
    add_backend "trino$i" "$i"
done                                                                                   }'
```

You can clean up by running

```shell
docker kill trino1 && docker rm trino1
docker kill trino2 && docker rm trino2
```
