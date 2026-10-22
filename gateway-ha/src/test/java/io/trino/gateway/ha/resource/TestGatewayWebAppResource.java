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
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.File;
import java.io.IOException;

import static io.trino.gateway.ha.util.TestcontainersUtils.createPostgreSqlContainer;
import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(Lifecycle.PER_CLASS)
final class TestGatewayWebAppResource
{
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final PostgreSQLContainer postgresql = createPostgreSqlContainer();
    private final OkHttpClient httpClient = new OkHttpClient();
    private final int routerPort = 23001 + (int) (Math.random() * 1000);

    @BeforeAll
    void setup()
            throws Exception
    {
        postgresql.start();
        // This configuration has no routing rules section, so the rules engine is not in use
        File configFile = HaGatewayTestUtils.buildGatewayConfig(
                postgresql, routerPort, "test-config-no-monitor-template.yml");
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
}
