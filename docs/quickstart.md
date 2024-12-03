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

# Set the version for the Gateway Jar
VERSION=12

# Function to copy necessary files to the current directory
copy_files() {
    # Check and get the Gateway Jar
    if [[ -f "gateway-ha.jar" ]]; then
        echo "Found gateway-ha.jar file in current directory."
    else
        echo "Fetching gateway-ha.jar version $VERSION from Maven Central repository."
        curl -O "https://repo1.maven.org/maven2/io/trino/gateway/gateway-ha/${VERSION}/gateway-ha-${VERSION}-jar-with-dependencies.jar"
        mv "gateway-ha-${VERSION}-jar-with-dependencies.jar" ./gateway-ha.jar
    fi

    # Check and get the Config.yaml
    if [[ -f "quickstart-config.yaml" ]]; then
        echo "Found quickstart-config.yaml file in current directory."
    else
        cp ../docs/quickstart-config.yaml ./quickstart-config.yaml
    fi

    # Check and get the postgres.sql
    if [[ -f "gateway-ha-persistence-postgres.sql" ]]; then
        echo "Found gateway-ha-persistence-postgres.sql file in current directory."
    else
        cp ../gateway-ha/src/main/resources/gateway-ha-persistence-postgres.sql ./gateway-ha-persistence-postgres.sql
    fi
}

# Function to check if PostgreSQL database is running and start it if not
start_postgres_db() {
    if docker ps --format '{{.Names}}' | grep -q '^local-postgres$'; then
        echo "PostgreSQL database container 'local-postgres' is already running. Only starting Trino Gateway."
    else
        echo "Starting PostgreSQL database container 'local-postgres'."
        
        # Set the password for PostgreSQL
        export PGPASSWORD=mysecretpassword
        
        # Run PostgreSQL container with necessary configurations
        docker run -v "$(pwd)"/gateway-ha-persistence-postgres.sql:/tmp/gateway-ha-persistence-postgres.sql \
            --name local-postgres -p 5432:5432 \
            -e POSTGRES_PASSWORD=$PGPASSWORD -d postgres:latest
        
        # Wait for the database to initialize
        sleep 5

        # Initialize the database and load the SQL script
        docker exec local-postgres psql -U postgres -h localhost -c 'CREATE DATABASE gateway'
        docker exec local-postgres psql -U postgres -h localhost -d gateway \
            -f /tmp/gateway-ha-persistence-postgres.sql
    fi
}

# Main execution flow
copy_files          # Copy necessary files to current directory
start_postgres_db   # Start PostgreSQL database if not running

# Start Trino Gateway server.
echo "Starting Trino Gateway server..."
java -Xmx1g -jar ./gateway-ha.jar ./quickstart-config.yaml
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

# Start a pair of Trino servers on different ports
docker run --name trino1 -d -p 8081:8080 \
    -e JAVA_TOOL_OPTIONS="-Dhttp-server.process-forwarded=true" trinodb/trino

docker run --name trino2 -d -p 8082:8080 \
    -e JAVA_TOOL_OPTIONS="-Dhttp-server.process-forwarded=true" trinodb/trino

# Add the Trino servers as Gateway backends
add_backend() {
    local name=$1
    local proxy_to=$2

    curl -H "Content-Type: application/json" -X POST \
        localhost:8080/gateway/backend/modify/add \
        -d "{
              \"name\": \"$name\",
              \"proxyTo\": \"$proxy_to\",
              \"active\": true,
              \"routingGroup\": \"adhoc\"
            }"
}

# Adding Trino servers as backends
add_backend "trino1" "http://localhost:8081"
add_backend "trino2" "http://localhost:8082"                                                                                       }'
```

You can clean up by running

```shell
docker kill trino1 && docker rm trino1
docker kill trino2 && docker rm trino2
```
