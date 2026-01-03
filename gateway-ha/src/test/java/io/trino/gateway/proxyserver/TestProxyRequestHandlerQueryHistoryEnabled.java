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
package io.trino.gateway.proxyserver;

import io.trino.gateway.ha.HaGatewayLauncher;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.PostgreSQLContainer;

import java.io.File;
import java.util.List;
import java.util.Map;

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static com.google.common.net.MediaType.JSON_UTF_8;
import static com.google.common.util.concurrent.Uninterruptibles.sleepUninterruptibly;
import static io.trino.gateway.ha.HaGatewayTestUtils.buildGatewayConfig;
import static io.trino.gateway.ha.HaGatewayTestUtils.prepareMockBackend;
import static io.trino.gateway.ha.HaGatewayTestUtils.setUpBackend;
import static io.trino.gateway.ha.handler.HttpUtils.V1_STATEMENT_PATH;
import static io.trino.gateway.ha.util.TestcontainersUtils.createPostgreSqlContainer;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@TestInstance(PER_CLASS)
final class TestProxyRequestHandlerQueryHistoryEnabled
{
    private final OkHttpClient httpClient = new OkHttpClient();
    private final MockWebServer mockTrinoServer = new MockWebServer();
    private final PostgreSQLContainer postgresql = createPostgreSqlContainer();

    private final int routerPort = 23001 + (int) (Math.random() * 1000);
    private final int customBackendPort = 23000 + (int) (Math.random() * 1000);

    private static final MediaType MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");
    private static final String TEST_QUERY_ID = "20240101_123456_00001_xyzab";

    private final String healthCheckEndpoint = "/v1/info";
    private Jdbi jdbi;

    @BeforeAll
    void setup()
            throws Exception
    {
        prepareMockBackend(mockTrinoServer, customBackendPort, "default custom response");
        mockTrinoServer.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request)
            {
                if (request.getPath().equals(healthCheckEndpoint)) {
                    return new MockResponse().setResponseCode(200)
                            .setHeader(CONTENT_TYPE, JSON_UTF_8)
                            .setBody("{\"starting\": false}");
                }

                if (request.getMethod().equals("POST") && request.getPath().equals(V1_STATEMENT_PATH)) {
                    return new MockResponse().setResponseCode(200)
                            .setHeader(CONTENT_TYPE, JSON_UTF_8)
                            .setBody("{\"id\": \"" + TEST_QUERY_ID + "\", \"stats\": {}}");
                }

                return new MockResponse().setResponseCode(404);
            }
        });

        postgresql.start();

        // Use default test config (query history enabled by default)
        File testConfigFile = buildGatewayConfig(postgresql, routerPort, "test-config-template.yml");

        String[] args = {testConfigFile.getAbsolutePath()};
        HaGatewayLauncher.main(args);

        setUpBackend("custom-enabled", "http://localhost:" + customBackendPort, "externalUrl", true, "adhoc", routerPort);

        jdbi = Jdbi.create(postgresql.getJdbcUrl(), postgresql.getUsername(), postgresql.getPassword());
    }

    @AfterAll
    void cleanup()
            throws Exception
    {
        mockTrinoServer.shutdown();
    }

    @Test
    void testQueryHistoryRecordedWhenEnabled()
            throws Exception
    {
        String url = "http://localhost:" + routerPort + V1_STATEMENT_PATH;
        String testQuery = "SELECT 2";
        RequestBody requestBody = RequestBody.create(testQuery, MEDIA_TYPE);

        Request postRequest = new Request.Builder()
                .url(url)
                .addHeader("X-Trino-User", "test-user-enabled")
                .post(requestBody)
                .build();

        try (Response response = httpClient.newCall(postRequest).execute()) {
            assertThat(response.isSuccessful()).isTrue();
            assertThat(response.body()).isNotNull();
            String responseBody = response.body().string();
            assertThat(responseBody).contains(TEST_QUERY_ID);
        }

        // Wait a bit for async query history submission
        sleepUninterruptibly(2, SECONDS);

        // Verify that query history WAS recorded in the database
        List<Map<String, Object>> queryHistory = jdbi.withHandle(handle ->
                handle.createQuery("SELECT * FROM query_history WHERE query_id = :queryId")
                        .bind("queryId", TEST_QUERY_ID)
                        .mapToMap()
                        .list());

        assertThat(queryHistory).hasSize(1);
        assertThat(queryHistory.get(0).get("query_id")).isEqualTo(TEST_QUERY_ID);
        assertThat(queryHistory.get(0).get("user_name")).isEqualTo("test-user-enabled");
        assertThat(queryHistory.get(0).get("query_text")).isEqualTo(testQuery);
    }
}
