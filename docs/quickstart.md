**Trino Gateway documentation**

<table>
  <tr>
    <td><a href="configuration.md">Configuration</a></td>
    <td><a href="design.md">Design</a></td>
    <td><a href="development.md">Development</a></td>
    <td><b><a href="quickstart.md">Quickstart</a></b></td>
    <td><a href="security.md">Security</a></td>
    <td><a href="operation.md">Operation</a></td>
    <td><a href="gateway-api.md">Gateway API</a></td>
    <td><a href="resource-groups-api.md">Resource groups API</a></td>
    <td><a href="routing-rules.md">Routing rules</a></td>
    <td><a href="references.md">References</a></td>
    <td><a href="release-notes.md">Release notes</a></td>
  </tr>
</table>

# Quickstart

The scripts from this quickstart guide set up a local environment consisting of
two Trino servers and a PostgreSQL database running in Docker, and a Trino
Gateway server running in the host operating system. If you are using a Mac and
have the `brew` package manager installed, this script attempts to install
`psql`, if not you must install `psql` through other means.

## Start Trino Gateway server

The following script starts a Trino Gateway server using the 
[Quickstart Configuration](quickstart-config.yaml) with the request service
at http://localhost:9080, the application service at http://localhost:9081,
and the Admin service at http://localhost:9082. It also starts a dockerized
PostgreSQL database at localhost:5432.

```shell
#!/usr/bin/env sh

VERSION=4

#Get the Gateway Jar
curl https://repo1.maven.org/maven2/io/trino/gateway/gateway-ha/${VERSION}/gateway-ha-${VERSION}-jar-with-dependencies.jar -o ./gateway-ha.jar

#Start the persistence DB
if ! $(command -v psql >/dev/null); then
    if ! $(command -v brew >/dev/null); then
        echo 'Please install psql and rerun';
        exit 1
    else
        brew install libpq
    fi
fi

export PGPASSWORD=mysecretpassword
docker run --name local-postgres -p 5432:5432 -e POSTGRES_PASSWORD=$PGPASSWORD -d postgres:latest
#make sure the DB has time to initialize
sleep 5

#Initialize the DB
curl https://raw.githubusercontent.com/trinodb/trino-gateway/main/gateway-ha/src/main/resources/gateway-ha-persistence-postgres.sql > ./gateway-ha-persistence-postgres.sql 
psql -U postgres -h localhost -c 'CREATE DATABASE gateway'
psql  -U postgres -h localhost -d gateway -f ./gateway-ha-persistence-postgres.sql 

#Start the DB
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
to the Trino  Gateway server started by the preceding script.

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
