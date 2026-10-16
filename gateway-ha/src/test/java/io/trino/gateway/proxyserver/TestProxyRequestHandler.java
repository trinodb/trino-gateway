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

import io.airlift.http.client.HttpClient;
import io.trino.gateway.ha.HaGatewayLauncher;
import io.trino.gateway.ha.clustermonitor.ClusterStats;
import io.trino.gateway.ha.clustermonitor.TrinoStatus;
import io.trino.gateway.ha.config.HaGatewayConfiguration;
import io.trino.gateway.ha.config.ProxyBackendConfiguration;
import io.trino.gateway.ha.handler.schema.RoutingDestination;
import io.trino.gateway.ha.router.BackendStateManager;
import io.trino.gateway.ha.router.GatewayBackendManager;
import io.trino.gateway.ha.router.OAuth2RoutingStore;
import io.trino.gateway.ha.router.QueryHistoryManager;
import io.trino.gateway.ha.router.RoutingManager;
import jakarta.ws.rs.WebApplicationException;
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
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.File;
import java.net.URI;
import java.util.List;
import java.util.Optional;

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static com.google.common.net.MediaType.JSON_UTF_8;
import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.http.client.Request.Builder.preparePost;
import static io.airlift.http.client.StaticBodyGenerator.createStaticBodyGenerator;
import static io.trino.gateway.ha.HaGatewayTestUtils.buildGatewayConfig;
import static io.trino.gateway.ha.HaGatewayTestUtils.prepareMockBackend;
import static io.trino.gateway.ha.HaGatewayTestUtils.setUpBackend;
import static io.trino.gateway.ha.handler.HttpUtils.V1_STATEMENT_PATH;
import static io.trino.gateway.ha.handler.ProxyUtils.SOURCE_HEADER;
import static io.trino.gateway.ha.util.TestcontainersUtils.createPostgreSqlContainer;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@TestInstance(PER_CLASS)
final class TestProxyRequestHandler
{
    private final OkHttpClient httpClient = new OkHttpClient();
    private final MockWebServer mockTrinoServer = new MockWebServer();
    private final PostgreSQLContainer postgresql = createPostgreSqlContainer();

    private final int routerPort = 21001 + (int) (Math.random() * 1000);
    private final int customBackendPort = 21000 + (int) (Math.random() * 1000);

    private static final String OK = "OK";
    private static final int NOT_FOUND = 404;
    private static final MediaType MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");
    private static final String HEAD_ENDPOINT = "/v1/query";

    private final String customPutEndpoint = "/v1/custom"; // this is enabled in test-config-template.yml
    private final String healthCheckEndpoint = "/v1/info";

    @BeforeAll
    void setup()
            throws Exception
    {
        prepareMockBackend(mockTrinoServer, customBackendPort, "default custom response");
        mockTrinoServer.setDispatcher(new Dispatcher()
        {
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

                if (request.getMethod().equals("HEAD") && request.getPath().equals(HEAD_ENDPOINT)) {
                    return new MockResponse().setResponseCode(200)
                            .setHeader(CONTENT_TYPE, JSON_UTF_8);
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
    void testHeadRequestHandler()
            throws Exception
    {
        String url = "http://localhost:" + routerPort + HEAD_ENDPOINT;

        Request headRequest = new Request.Builder().url(url).head().build();
        try (Response response = httpClient.newCall(headRequest).execute()) {
            assertThat(response.code()).isEqualTo(200);
            assertThat(response.header("Content-Type")).isEqualTo("application/json;charset=utf-8");
        }

        Request getRequest = new Request.Builder().url(url).get().build();
        try (Response response = httpClient.newCall(getRequest).execute()) {
            assertThat(response.code()).isEqualTo(NOT_FOUND);
        }
    }

    @Test
    void testGetQueryDetailsFromRequest()
    {
        // A sample query longer than 200 characters to test against truncation.
        String longQuery =
                """
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

    @Test
    void testRedactUriForLog()
    {
        // The backend URI has the proxyTo base path before the OAuth2 path
        assertThat(ProxyRequestHandler.redactUriForLog(URI.create("https://coord-a:8443/trino/oauth2/token/11111111-2222-3333-4444-555555555555")))
                .isEqualTo("https://coord-a:8443/trino/oauth2/token/<redacted>");
        assertThat(ProxyRequestHandler.redactUriForLog(URI.create("https://coord-a:8443/v1/statement")))
                .isEqualTo("https://coord-a:8443/v1/statement");
    }

    @Test
    void testFindInactiveUnhealthyBackend()
    {
        ProxyBackendConfiguration backend = backend(false);
        // trailing slash on the configured proxyTo must not defeat the match
        backend.setProxyTo("http://trino-a:8080/");

        assertThat(handler(backend, TrinoStatus.UNHEALTHY).findInactiveUnhealthyBackend("http://trino-a:8080"))
                .containsSame(backend);
    }

    @Test
    void testFindInactiveUnhealthyBackendIgnoresActiveBackend()
    {
        assertThat(handler(backend(true), TrinoStatus.UNHEALTHY).findInactiveUnhealthyBackend("http://trino-a:8080")).isEmpty();
    }

    @Test
    void testFindInactiveUnhealthyBackendIgnoresHealthyBackend()
    {
        assertThat(handler(backend(false), TrinoStatus.HEALTHY).findInactiveUnhealthyBackend("http://trino-a:8080")).isEmpty();
    }

    @Test
    void testFindInactiveUnhealthyBackendIgnoresUnknownHost()
    {
        assertThat(handler(backend(false), TrinoStatus.UNHEALTHY).findInactiveUnhealthyBackend("http://other:8080")).isEmpty();
    }

    @Test
    void testFindInactiveUnhealthyBackendFallsBackWhenLookupFails()
    {
        GatewayBackendManager backendManager = mock(GatewayBackendManager.class);
        when(backendManager.getAllBackends()).thenThrow(new IllegalStateException("cache load failed"));

        assertThat(handler(backendManager, mock(BackendStateManager.class)).findInactiveUnhealthyBackend("http://trino-a:8080")).isEmpty();
    }

    @Test
    void testProxyFailureReturns503ForInactiveUnhealthyBackend()
    {
        RoutingDestination destination = destination();

        assertThatThrownBy(() -> handler(backend(false), TrinoStatus.UNHEALTHY)
                .handleProxyException(proxyRequest(destination), new ProxyException("connection refused"), destination))
                .isInstanceOfSatisfying(WebApplicationException.class, e -> {
                    assertThat(e.getResponse().getStatus()).isEqualTo(503);
                    assertThat((String) e.getResponse().getEntity()).isEqualTo("Backend cluster trino-a is inactive and unreachable");
                });
    }

    @Test
    void testProxyFailureReturns502ForActiveBackend()
    {
        RoutingDestination destination = destination();

        assertThatThrownBy(() -> handler(backend(true), TrinoStatus.UNHEALTHY)
                .handleProxyException(proxyRequest(destination), new ProxyException("connection refused"), destination))
                .isInstanceOfSatisfying(WebApplicationException.class, e -> {
                    assertThat(e.getResponse().getStatus()).isEqualTo(502);
                    assertThat((String) e.getResponse().getEntity()).isEqualTo("connection refused");
                });
    }

    @Test
    void testProxyFailureReturns502ForInactiveHealthyBackend()
    {
        RoutingDestination destination = destination();

        assertThatThrownBy(() -> handler(backend(false), TrinoStatus.HEALTHY)
                .handleProxyException(proxyRequest(destination), new ProxyException("connection refused"), destination))
                .isInstanceOfSatisfying(WebApplicationException.class, e -> assertThat(e.getResponse().getStatus()).isEqualTo(502));
    }

    private static RoutingDestination destination()
    {
        return new RoutingDestination("adhoc", "http://trino-a:8080", URI.create("http://trino-a:8080/v1/statement"), "http://trino-a:8080");
    }

    private static io.airlift.http.client.Request proxyRequest(RoutingDestination destination)
    {
        return prepareGet().setUri(destination.clusterUri()).build();
    }

    private static ProxyBackendConfiguration backend(boolean active)
    {
        ProxyBackendConfiguration backend = new ProxyBackendConfiguration();
        backend.setName("trino-a");
        backend.setProxyTo("http://trino-a:8080");
        backend.setActive(active);
        return backend;
    }

    private static ProxyRequestHandler handler(ProxyBackendConfiguration backend, TrinoStatus status)
    {
        GatewayBackendManager backendManager = mock(GatewayBackendManager.class);
        when(backendManager.getAllBackends()).thenReturn(List.of(backend));
        BackendStateManager stateManager = mock(BackendStateManager.class);
        when(stateManager.getBackendState(backend)).thenReturn(ClusterStats.builder("trino-a").trinoStatus(status).build());
        return handler(backendManager, stateManager);
    }

    private static ProxyRequestHandler handler(GatewayBackendManager backendManager, BackendStateManager stateManager)
    {
        return new ProxyRequestHandler(
                mock(HttpClient.class),
                mock(RoutingManager.class),
                mock(QueryHistoryManager.class),
                backendManager,
                stateManager,
                mock(OAuth2RoutingStore.class),
                new HaGatewayConfiguration());
    }
}
