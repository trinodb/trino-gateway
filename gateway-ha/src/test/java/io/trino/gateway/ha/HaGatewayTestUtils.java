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
package io.trino.gateway.ha;

import io.airlift.json.JsonCodec;
import io.airlift.log.Logger;
import io.trino.gateway.ha.clustermonitor.ClusterStats;
import io.trino.gateway.ha.clustermonitor.TrinoStatus;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.testcontainers.containers.OracleContainer;
import org.testcontainers.containers.PostgreSQLContainer;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.net.HttpHeaders.CONTENT_ENCODING;
import static com.google.common.net.MediaType.PLAIN_TEXT_UTF_8;
import static com.google.common.util.concurrent.Uninterruptibles.sleepUninterruptibly;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class HaGatewayTestUtils
{
    private static final Logger log = Logger.get(HaGatewayTestUtils.class);
    private static final OkHttpClient httpClient = new OkHttpClient();

    private HaGatewayTestUtils() {}

    public static void prepareMockBackend(
            MockWebServer backend, int customBackendPort, String expectedResponse)
            throws IOException
    {
        backend.start(customBackendPort);
        backend.enqueue(new MockResponse()
                .setBody(expectedResponse)
                .addHeader(CONTENT_ENCODING, PLAIN_TEXT_UTF_8)
                .setResponseCode(200));
    }

    public static void seedRequiredData(String h2DbFilePath)
    {
        String jdbcUrl = "jdbc:h2:" + h2DbFilePath;
        Jdbi jdbi = Jdbi.create(jdbcUrl, "sa", "sa");
        try (Handle handle = jdbi.open()) {
            handle.createUpdate(HaGatewayTestUtils.getResourceFileContent("gateway-ha-persistence-mysql.sql"))
                    .execute();
        }
    }

    public static File buildGatewayConfig(PostgreSQLContainer postgreSqlContainer, int routerPort, String configFile)
            throws Exception
    {
        URL resource = HaGatewayTestUtils.class.getClassLoader().getResource("auth/localhost.jks");
        String configStr =
                getResourceFileContent(configFile)
                        .replace("REQUEST_ROUTER_PORT", String.valueOf(routerPort))
                        .replace("POSTGRESQL_JDBC_URL", postgreSqlContainer.getJdbcUrl())
                        .replace("POSTGRESQL_USER", postgreSqlContainer.getUsername())
                        .replace("POSTGRESQL_PASSWORD", postgreSqlContainer.getPassword())
                        .replace(
                                "APPLICATION_CONNECTOR_PORT", String.valueOf(30000 + (int) (Math.random() * 1000)))
                        .replace("ADMIN_CONNECTOR_PORT", String.valueOf(31000 + (int) (Math.random() * 1000)))
                        .replace("LOCALHOST_JKS", Path.of(resource.toURI()).toString())
                        .replace("RESOURCES_DIR", Path.of("src", "test", "resources").toAbsolutePath().toString());

        File target = File.createTempFile("config-" + System.currentTimeMillis(), "config.yaml");

        try (BufferedWriter writer = Files.newBufferedWriter(target.toPath(), UTF_8)) {
            writer.append(configStr);
        }

        log.info("Test Gateway Config \n[%s]", configStr);
        return target;
    }

    public static String getResourceFileContent(String fileName)
    {
        StringBuilder sb = new StringBuilder();
        InputStream inputStream =
                HaGatewayTestUtils.class.getClassLoader().getResourceAsStream(fileName);
        Scanner scn = new Scanner(inputStream, UTF_8);
        while (scn.hasNextLine()) {
            sb.append(scn.nextLine()).append("\n");
        }
        return sb.toString();
    }

    public static void setUpBackend(
            String name,
            String proxyTo,
            String externalUrl,
            boolean active,
            String routingGroup,
            int routerPort)
            throws Exception
    {
        RequestBody requestBody =
                RequestBody.create(
                        "{ \"name\": \""
                                + name
                                + "\",\"proxyTo\": \""
                                + proxyTo
                                + "\",\"externalUrl\": \""
                                + externalUrl
                                + "\",\"active\": "
                                + active
                                + ",\"routingGroup\": \""
                                + routingGroup
                                + "\"}",
                        MediaType.parse("application/json; charset=utf-8"));
        Request request =
                new Request.Builder()
                        .url("http://localhost:" + routerPort + "/entity?entityType=GATEWAY_BACKEND")
                        .post(requestBody)
                        .build();
        Response response = httpClient.newCall(request).execute();
        assertThat(response.isSuccessful()).isTrue();
        verifyTrinoStatus(routerPort, name);
    }

    public static OracleContainer getOracleContainer()
    {
        // reference for GITHUB_ACTIONS: https://docs.github.com/en/actions/writing-workflows/choosing-what-your-workflow-does/store-information-in-variables#default-environment-variables
        assumeTrue("true".equals(System.getenv("GITHUB_ACTIONS"))
                || "x86_64".equalsIgnoreCase(System.getProperty("os.arch"))
                || "true".equals(System.getenv("TG_RUN_ORACLE_TESTS")));
        return new OracleContainer("gvenzl/oracle-xe:18.4.0-slim");
    }

    private static void verifyTrinoStatus(int port, String name)
            throws IOException
    {
        Request getBackendStateRequest = new Request.Builder()
                .url(format("http://localhost:%s/api/public/backends/%s/state", port, name))
                .get()
                .build();

        for (int i = 0; i < 10; i++) {
            try (Response getBackendStateResponse = httpClient.newCall(getBackendStateRequest).execute()) {
                checkState(getBackendStateResponse.isSuccessful());
                JsonCodec<ClusterStats> responseCodec = JsonCodec.jsonCodec(ClusterStats.class);
                ResponseBody getBackendStateResponseBody = requireNonNull(getBackendStateResponse.body(), "getBackendStateResponse.body() is null");
                ClusterStats clusterStats = responseCodec.fromJson(getBackendStateResponseBody.string());
                if (clusterStats.trinoStatus() == TrinoStatus.HEALTHY) {
                    return;
                }
            }
            sleepUninterruptibly(1, TimeUnit.SECONDS);
        }
        throw new IllegalStateException("Trino cluster is not healthy");
    }
}
