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
        <li><a href="routing-logic.md">Routing logic</a></li>
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

# Quickstart

The scripts from this quickstart guide set up a local environment consisting of
two Trino servers and a PostgreSQL database running in Docker, and a Trino
Gateway server running in the host operating system. 

## Start Trino Gateway server

The following script starts a Trino Gateway server using the 
[Quickstart configuration](quickstart-config.yaml) with the request service
at http://localhost:9080, the application service at http://localhost:9081,
and the Admin service at http://localhost:9082. It also starts a dockerized
PostgreSQL database at localhost:5432.

To start the server, copy the script below to a temporary directory 
under the project root folder, and run it at the temporary directory.

It  copies the following, necessary files to current directory:

- gateway-ha.jar
- gateway-ha-persistence-postgres.sql
- quickstart-config.yaml

```shell
#!/usr/bin/env sh

VERSION=7

# Copy necessary files to current directory

# Check and get the Gateway Jar
if [[ -f "gateway-ha.jar" ]]; then
    echo "Found gateway-har.jar file in current directory."
else
    echo "Failed to find gateway-ha.jar in current directory. Fetching version $VERSION from Maven Central repository."
    curl https://repo1.maven.org/maven2/io/trino/gateway/gateway-ha/${VERSION}/gateway-ha-${VERSION}-jar-with-dependencies.jar -o ./gateway-ha.jar
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

#Check if DB is running
if docker ps --format '{{.Names}}' | grep -q '^local-postgres$'; then
    echo "PostgreSQL database container 'localhost-postgres' is already running. Only starting Trino Gateway."
else
    echo "PostgreSQL database container 'localhost-postgres' is not running. Proceeding to initialize and run database server."
    export PGPASSWORD=mysecretpassword
    docker run -v "$(pwd)"/gateway-ha-persistence-postgres.sql:/tmp/gateway-ha-persistence-postgres.sql --name local-postgres -p 5432:5432 -e POSTGRES_PASSWORD=$PGPASSWORD -d postgres:latest
    #Make sure the DB has time to initialize
    sleep 5

    #Initialize the DB
    docker exec local-postgres psql -U postgres -h localhost -c 'CREATE DATABASE gateway'
    docker exec local-postgres psql -U postgres -h localhost -d gateway -f /tmp/gateway-ha-persistence-postgres.sql
fi


#Start Trino Gateway server.
java -Xmx1g --add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.net=ALL-UNNAMED -jar ./gateway-ha.jar server ./quickstart-config.yaml
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

#Start a pair of trino servers on different ports
docker run --name trino1 -d -p 8081:8080 trinodb/trino
docker run --name trino2 -d -p 8082:8080 trinodb/trino

#Add the trino servers as Gateway backends
curl -H "Content-Type: application/json" -X POST localhost:9080/gateway/backend/modify/add -d '{"name": "trino1",
                                                                                                "proxyTo": "http://localhost:8081",
                                                                                                "active": true,
                                                                                                "routingGroup": "adhoc"
                                                                                              }'
curl -H "Content-Type: application/json" -X POST localhost:9080/gateway/backend/modify/add -d '{"name": "trino2",
                                                                                                "proxyTo": "http://localhost:8082",
                                                                                                "active": true,
                                                                                                "routingGroup": "adhoc"
                                                                                              }'
```

You can clean up by running

```shell
docker kill trino1 && docker rm trino1
docker kill trino2 && docker rm trino2
```
