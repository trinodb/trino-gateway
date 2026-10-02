/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.trino.gateway;

import io.airlift.log.Logger;
import io.airlift.log.Logging;
import io.trino.gateway.ha.HaGatewayLauncher;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.trino.TrinoContainer;

import java.io.IOException;
import java.util.List;

import static com.google.common.base.Preconditions.checkState;
import static io.trino.gateway.ha.util.TestcontainersUtils.createPostgreSqlContainer;
import static org.testcontainers.utility.MountableFile.forClasspathResource;

public final class TrinoGatewayRunner
{
    // Matches http-server.http.port in gateway-ha/config.yaml
    private static final int GATEWAY_PORT = 8080;

    private static final OkHttpClient httpClient = new OkHttpClient();

    private TrinoGatewayRunner() {}

    public static void main(String[] args)
            throws Exception
    {
        Logging.initialize();
        Logger log = Logger.get(TrinoGatewayRunner.class);

        TrinoContainer trino1 = new TrinoContainer("trinodb/trino:466");
        trino1.setPortBindings(List.of("8081:8080"));
        trino1.withCopyFileToContainer(forClasspathResource("trino-config.properties"), "/etc/trino/config.properties");
        trino1.start();
        TrinoContainer trino2 = new TrinoContainer("trinodb/trino:466");
        trino2.setPortBindings(List.of("8082:8080"));
        trino2.withCopyFileToContainer(forClasspathResource("trino-config.properties"), "/etc/trino/config.properties");
        trino2.start();

        PostgreSQLContainer postgres = createPostgreSqlContainer();
        postgres.withUsername("trino_gateway_db_admin");
        postgres.withPassword("P0stG&es");
        postgres.withDatabaseName("trino_gateway_db");
        postgres.setPortBindings(List.of("5432:5432"));
        postgres.start();

        MySQLContainer mysql = new MySQLContainer("mysql:5.7");
        mysql.withUsername("root");
        mysql.withPassword("root123");
        mysql.withDatabaseName("trinogateway");
        mysql.setPortBindings(List.of("3306:3306"));
        mysql.start();

        OpenTracingCollector tracingCollector = new OpenTracingCollector();
        tracingCollector.start();

        HaGatewayLauncher.main(new String[] {"gateway-ha/config.yaml"});

        // Flyway creates the schema while the gateway starts, so the backends can only be added afterwards
        addBackend("trino-1", "http://localhost:8081");
        addBackend("trino-2", "http://localhost:8082");

        log.info("======== SERVER STARTED ========");
        log.info("Tracing: http://localhost:16686");
    }

    private static void addBackend(String name, String proxyTo)
            throws IOException
    {
        RequestBody requestBody = RequestBody.create(
                """
                {"name": "%s", "proxyTo": "%s", "externalUrl": "%s", "active": true, "routingGroup": "adhoc"}
                """.formatted(name, proxyTo, proxyTo),
                MediaType.parse("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url("http://localhost:%s/entity?entityType=GATEWAY_BACKEND".formatted(GATEWAY_PORT))
                .post(requestBody)
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            checkState(response.isSuccessful(), "Failed to add backend %s, received status code %s", name, response.code());
        }
    }
}
