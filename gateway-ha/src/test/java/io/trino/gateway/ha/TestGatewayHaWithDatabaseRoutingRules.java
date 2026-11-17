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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.trino.gateway.ha.domain.RoutingRule;
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
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static com.google.common.net.MediaType.JSON_UTF_8;
import static io.trino.gateway.ha.HaGatewayTestUtils.VALID_CLIENT_TAGS;
import static io.trino.gateway.ha.handler.HttpUtils.CLIENT_TAGS_HEADER;
import static io.trino.gateway.ha.util.TestcontainersUtils.createPostgreSqlContainer;
import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
final class TestGatewayHaWithDatabaseRoutingRules
{
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient httpClient = new OkHttpClient();
    private final PostgreSQLContainer postgresql = createPostgreSqlContainer();
    private final MockWebServer adhocBackend = new MockWebServer();
    private final MockWebServer etlBackend = new MockWebServer();
    private final AtomicInteger adhocQueries = new AtomicInteger();
    private final AtomicInteger etlQueries = new AtomicInteger();
    private final int routerPort = 22001 + (int) (Math.random() * 1000);

    @BeforeAll
    void setup()
            throws Exception
    {
        postgresql.start();
        startBackend(adhocBackend, adhocQueries);
        startBackend(etlBackend, etlQueries);

        File testConfigFile = HaGatewayTestUtils.buildGatewayConfig(postgresql, routerPort, "test-config-with-routing-rules-database.yml");
        HaGatewayLauncher.main(new String[] {testConfigFile.getAbsolutePath()});

        HaGatewayTestUtils.setUpBackend("adhoc-backend", adhocBackend.url("/").toString(), "externalUrl", true, "adhoc", routerPort);
        HaGatewayTestUtils.setUpBackend("etl-backend", etlBackend.url("/").toString(), "externalUrl", true, "etl", routerPort);
    }

    @Test
    void testRuleCreatedThroughApiRoutesQueries()
            throws Exception
    {
        sendQuery("airflow");
        assertThat(adhocQueries.get()).isEqualTo(1);
        assertThat(etlQueries.get()).isZero();

        RoutingRule rule = new RoutingRule(
                "airflow",
                "route airflow to etl",
                0,
                List.of("result.put(\"routingGroup\", \"etl\")"),
                "request.getHeader(\"X-Trino-Source\") == \"airflow\"");
        JsonNode created = post("/webapp/createRoutingRule", rule);
        assertThat(created.path("code").asInt()).isEqualTo(200);

        // The rule is picked up after rulesRefreshPeriod (1s in test config) without restarting the gateway.
        // Poll until the rule takes effect, rather than a fixed sleep that could flake on slow CI.
        long deadline = System.currentTimeMillis() + 10_000;
        while (etlQueries.get() == 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(200);
            sendQuery("airflow");
        }
        assertThat(etlQueries.get()).isPositive();

        int adhocBefore = adhocQueries.get();
        sendQuery("jupyter");
        assertThat(adhocQueries.get()).isEqualTo(adhocBefore + 1);
    }

    @Test
    void testRoutingRulesMetricsExported()
            throws IOException
    {
        // Dashboards and alerts depend on these names, which come from the class passed to exportWithGeneratedName
        Request request = new Request.Builder()
                .url("http://localhost:" + routerPort + "/metrics")
                .get()
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            assertThat(response.code()).isEqualTo(200);
            assertThat(response.body().string()).contains(
                    "io_trino_gateway_ha_router_name_RulesEngineRoutingGroupSelector_RefreshFailures",
                    "io_trino_gateway_ha_router_name_RulesEngineRoutingGroupSelector_RuleEvaluationFailures",
                    "io_trino_gateway_ha_router_name_RulesEngineRoutingGroupSelector_LoadedRulesCount",
                    "io_trino_gateway_ha_router_name_RulesEngineRoutingGroupSelector_SkippedRulesCount",
                    "io_trino_gateway_ha_router_name_RulesEngineRoutingGroupSelector_SecondsSinceLastSuccessfulRefresh");
        }
    }

    @AfterAll
    void cleanup()
            throws IOException
    {
        adhocBackend.shutdown();
        etlBackend.shutdown();
        postgresql.close();
    }

    private static void startBackend(MockWebServer backend, AtomicInteger queries)
            throws IOException
    {
        backend.setDispatcher(new Dispatcher()
        {
            @Override
            public MockResponse dispatch(RecordedRequest request)
            {
                if ("/v1/info".equals(request.getPath())) {
                    return new MockResponse().setResponseCode(200)
                            .setHeader(CONTENT_TYPE, JSON_UTF_8)
                            .setBody("{\"starting\": false}");
                }
                if ("/v1/statement".equals(request.getPath())) {
                    queries.incrementAndGet();
                }
                return new MockResponse().setResponseCode(200)
                        .setHeader(CONTENT_TYPE, JSON_UTF_8)
                        .setBody("{}");
            }
        });
        backend.start();
    }

    private void sendQuery(String source)
            throws IOException
    {
        Request request = new Request.Builder()
                .url("http://localhost:" + routerPort + "/v1/statement")
                .addHeader("X-Trino-User", "test")
                .addHeader("X-Trino-Source", source)
                .addHeader(CLIENT_TAGS_HEADER, VALID_CLIENT_TAGS)
                .post(RequestBody.create("SELECT 1", JSON))
                .build();
        httpClient.newCall(request).execute().close();
    }

    private JsonNode post(String path, Object body)
            throws IOException
    {
        Request request = new Request.Builder()
                .url("http://localhost:" + routerPort + path)
                .post(RequestBody.create(OBJECT_MAPPER.writeValueAsString(body), JSON))
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            assertThat(response.code()).isEqualTo(200);
            return OBJECT_MAPPER.readTree(response.body().string());
        }
    }
}
