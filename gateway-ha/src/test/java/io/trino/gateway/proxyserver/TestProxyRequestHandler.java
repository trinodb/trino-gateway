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
import io.trino.gateway.ha.router.QueryHistoryManager;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.PostgreSQLContainer;

import java.io.File;
import java.net.URI;
import java.util.Optional;

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static com.google.common.net.MediaType.JSON_UTF_8;
import static io.airlift.http.client.Request.Builder.preparePost;
import static io.airlift.http.client.StaticBodyGenerator.createStaticBodyGenerator;
import static io.trino.gateway.ha.HaGatewayTestUtils.buildGatewayConfig;
import static io.trino.gateway.ha.HaGatewayTestUtils.prepareMockBackend;
import static io.trino.gateway.ha.HaGatewayTestUtils.setUpBackend;
import static io.trino.gateway.ha.handler.HttpUtils.V1_STATEMENT_PATH;
import static io.trino.gateway.ha.handler.ProxyUtils.SOURCE_HEADER;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@TestInstance(PER_CLASS)
final class TestProxyRequestHandler
{
    private final OkHttpClient httpClient = new OkHttpClient();
    private final MockWebServer mockTrinoServer = new MockWebServer();
    private final PostgreSQLContainer postgresql = new PostgreSQLContainer("postgres:17");

    private final int routerPort = 21001 + (int) (Math.random() * 1000);
    private final int customBackendPort = 21000 + (int) (Math.random() * 1000);

    private static final String OK = "OK";
    private static final int NOT_FOUND = 404;
    private static final MediaType MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");

    private final String customPutEndpoint = "/v1/custom"; // this is enabled in test-config-template.yml
    private final String healthCheckEndpoint = "/v1/info";

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

                if (request.getMethod().equals("PUT") && request.getPath().equals(customPutEndpoint)) {
                    return new MockResponse().setResponseCode(200)
                            .setHeader(CONTENT_TYPE, JSON_UTF_8)
                            .setBody(OK);
                }

                return new MockResponse().setResponseCode(NOT_FOUND);
            }
        });

        postgresql.start();

        File testConfigFile = buildGatewayConfig(postgresql, routerPort, "test-config-template.yml");

        String[] args = {testConfigFile.getAbsolutePath()};
        HaGatewayLauncher.main(args);

        setUpBackend("custom", "http://localhost:" + customBackendPort, "externalUrl", true, "adhoc", routerPort);
    }

    @AfterAll
    void cleanup()
            throws Exception
    {
        mockTrinoServer.shutdown();
    }

    @Test
    void testPutRequestHandler()
            throws Exception
    {
        String url = "http://localhost:" + routerPort + customPutEndpoint;
        RequestBody requestBody = RequestBody.create("SELECT 1", MEDIA_TYPE);

        Request putRequest = new Request.Builder().url(url).put(requestBody).build();
        try (Response response = httpClient.newCall(putRequest).execute()) {
            assertThat(response.body()).isNotNull();
            assertThat(response.body().string()).isEqualTo(OK);
        }

        Request postRequest = new Request.Builder().url(url).post(requestBody).build();
        try (Response response = httpClient.newCall(postRequest).execute()) {
            assertThat(response.code()).isEqualTo(NOT_FOUND);
        }
    }

    @Test
    void testGetQueryDetailsFromRequest()
    {
        // A sample query longer than 200 characters to test against truncation.
        String longQuery = """
        SELECT
            c.customer_name,
            c.customer_region,
            COUNT(o.order_id) AS total_orders,
            SUM(o.order_value) AS total_revenue
        FROM
            hive.sales_data.customers AS c
        JOIN
            hive.sales_data.orders AS o
                ON c.customer_id = o.customer_id
        WHERE
            o.order_date >= date '2023-01-01'""";

        io.airlift.http.client.Request request = preparePost()
                .setUri(URI.create("http://localhost:" + routerPort + V1_STATEMENT_PATH))
                .addHeader(SOURCE_HEADER, "trino-cli")
                .setBodyGenerator(createStaticBodyGenerator(longQuery, UTF_8))
                .build();

        Optional<String> username = Optional.of("test_user");

        QueryHistoryManager.QueryDetail queryDetail = ProxyRequestHandler.getQueryDetailsFromRequest(request, username);

        assertThat(queryDetail.getQueryText()).isEqualTo(longQuery).hasSizeGreaterThan(200);
        assertThat(queryDetail.getUser()).isEqualTo(username.get());
        assertThat(queryDetail.getSource()).isEqualTo("trino-cli");
        assertThat(queryDetail.getBackendUrl()).isEqualTo("http://localhost:" + routerPort);
    }
}
