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
package io.trino.gateway.ha.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.trino.gateway.ha.HaGatewayLauncher;
import io.trino.gateway.ha.HaGatewayTestUtils;
import io.trino.gateway.ha.config.HaGatewayConfiguration;
import io.trino.gateway.ha.config.RoutingRulesConfiguration;
import io.trino.gateway.ha.config.RulesType;
import io.trino.gateway.ha.domain.Result;
import io.trino.gateway.ha.domain.RoutingRule;
import io.trino.gateway.ha.router.BackendStateManager;
import io.trino.gateway.ha.router.GatewayBackendManager;
import io.trino.gateway.ha.router.QueryHistoryManager;
import io.trino.gateway.ha.router.RoutingRulesManager;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static io.trino.gateway.ha.util.TestcontainersUtils.createPostgreSqlContainer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@Testcontainers
@TestInstance(Lifecycle.PER_CLASS)
final class TestGatewayWebAppResource
{
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Container
    private static final PostgreSQLContainer POSTGRESQL = createPostgreSqlContainer();

    private final OkHttpClient httpClient = new OkHttpClient();
    private final int routerPort = 28001 + (int) (Math.random() * 1000);

    @BeforeAll
    void setup()
            throws Exception
    {
        // This configuration has no routing rules section, so the rules engine is not in use
        File configFile = HaGatewayTestUtils.buildGatewayConfig(
                POSTGRESQL, routerPort, "test-config-no-monitor-template.yml");
        HaGatewayLauncher.main(new String[] {configFile.getAbsolutePath()});
    }

    @Test
    void testGetRoutingRulesWithoutRulesEngine()
            throws IOException
    {
        Request request = new Request.Builder()
                .url("http://localhost:%s/webapp/getRoutingRules".formatted(routerPort))
                .get()
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            assertThat(response.code()).isEqualTo(200);
            JsonNode result = OBJECT_MAPPER.readTree(response.body().string());
            assertThat(result.path("code").asInt()).isEqualTo(200);
            assertThat(result.path("data").isArray()).isTrue();
            assertThat(result.path("data")).isEmpty();
        }
    }

    @Test
    void testUpdateRoutingRulesWithoutRulesEngine()
            throws IOException
    {
        RequestBody body = RequestBody.create(
                "{\"name\":\"airflow\",\"actions\":[\"result.put(\\\"routingGroup\\\", \\\"etl\\\")\"],\"condition\":\"true\"}",
                MediaType.get("application/json"));
        Request request = new Request.Builder()
                .url("http://localhost:%s/webapp/updateRoutingRules".formatted(routerPort))
                .post(body)
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            assertThat(response.code()).isEqualTo(400);
            JsonNode result = OBJECT_MAPPER.readTree(response.body().string());
            assertThat(result.path("msg").asText()).isEqualTo("Routing rules engine is not enabled");
        }
    }

    @Test
    void testUpdateRoutingRulesWithExternalRules()
            throws IOException
    {
        RoutingRulesConfiguration routingRules = new RoutingRulesConfiguration();
        routingRules.setRulesEngineEnabled(true);
        routingRules.setRulesType(RulesType.EXTERNAL);
        HaGatewayConfiguration configuration = new HaGatewayConfiguration();
        configuration.setRoutingRules(routingRules);
        GatewayWebAppResource resource = new GatewayWebAppResource(
                mock(GatewayBackendManager.class),
                mock(QueryHistoryManager.class),
                mock(BackendStateManager.class),
                mock(RoutingRulesManager.class),
                configuration);

        jakarta.ws.rs.core.Response response = resource.updateRoutingRules(
                new RoutingRule("airflow", null, null, List.of("result.put(\"routingGroup\", \"etl\")"), "true"));
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(((Result<?>) response.getEntity()).getMsg()).isEqualTo("Routing rules are managed by an external service");
    }
}
