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
package io.trino.gateway.ha.router;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.airlift.json.JsonCodec;
import io.trino.gateway.ha.HaGatewayLauncher;
import io.trino.gateway.ha.HaGatewayTestUtils;
import io.trino.gateway.ha.config.UIConfiguration;
import io.trino.gateway.ha.domain.RoutingRule;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.trino.TrinoContainer;

import java.io.File;
import java.util.List;
import java.util.Map;

import static io.trino.gateway.ha.util.TestcontainersUtils.createPostgreSqlContainer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.testcontainers.utility.MountableFile.forClasspathResource;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
final class TestRoutingAPI
{
    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final OkHttpClient httpClient = new OkHttpClient();
    private TrinoContainer trino;
    private final PostgreSQLContainer postgresql = createPostgreSqlContainer();
    int routerPort = 21001 + (int) (Math.random() * 1000);
    int backendPort;

    @BeforeAll
    void setup()
            throws Exception
    {
        trino = new TrinoContainer("trinodb/trino");
        trino.withCopyFileToContainer(forClasspathResource("trino-config.properties"), "/etc/trino/config.properties");
        trino.start();

        backendPort = trino.getMappedPort(8080);

        postgresql.start();

        // seed database
        File testConfigFile =
                HaGatewayTestUtils.buildGatewayConfig(postgresql, routerPort, "test-config-with-routing-rules-api.yml");
        // Start Gateway
        String[] args = {testConfigFile.getAbsolutePath()};
        HaGatewayLauncher.main(args);
        // Now populate the backend
        HaGatewayTestUtils.setUpBackend(
                "trino1", "http://localhost:" + backendPort, "externalUrl", true, "adhoc", routerPort);
    }

    @Test
    void testGetRoutingRulesAPI()
            throws Exception
    {
        Request request =
                new Request.Builder()
                        .url("http://localhost:" + routerPort + "/webapp/getRoutingRules")
                        .get()
                        .build();
        Response response = httpClient.newCall(request).execute();

        String responseBody = response.body().string();
        JsonNode rootNode = OBJECT_MAPPER.readTree(responseBody);
        JsonNode dataNode = rootNode.path("data");

        assertThat(response.code()).isEqualTo(200);
        // The UI reads the rule's fields at the top level, next to the status
        JsonNode firstRule = dataNode.get(0);
        assertThat(firstRule.get("name").asText()).isEqualTo("airflow");
        assertThat(firstRule.get("description").asText()).isEqualTo("if query from airflow, route to etl group");
        assertThat(firstRule.get("priority").asInt()).isEqualTo(0);
        assertThat(firstRule.get("condition").asText()).isEqualTo("request.getHeader(\"X-Trino-Source\") == \"airflow\"");
        assertThat(firstRule.get("actions").get(0).asText()).isEqualTo("result.put(\"routingGroup\", \"etl\")");
        assertThat(firstRule.has("rule")).isFalse();
        assertThat(dataNode).allSatisfy(rule -> {
            assertThat(rule.get("active").asBoolean()).isTrue();
            assertThat(rule.hasNonNull("error")).isFalse();
        });
    }

    @Test
    void testUpdateRoutingRulesAPI()
            throws Exception
    {
        // Update routing rules with a new rule
        RoutingRule updatedRoutingRules = new RoutingRule("airflow", "if query from airflow, route to adhoc group", 0, List.of("result.put(\"routingGroup\", \"adhoc\")"), "request.getHeader(\"X-Trino-Source\") == \"JDBC\"");
        RequestBody requestBody = RequestBody.create(OBJECT_MAPPER.writeValueAsString(updatedRoutingRules), MediaType.parse("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                        .url("http://localhost:" + routerPort + "/webapp/updateRoutingRules")
                        .addHeader("Content-Type", "application/json")
                        .post(requestBody)
                        .build();
        Response response = httpClient.newCall(request).execute();

        assertThat(response.code()).isEqualTo(200);

        // Fetch the routing rules to see if the update was successful
        Request request2 =
                new Request.Builder()
                        .url("http://localhost:" + routerPort + "/webapp/getRoutingRules")
                        .get()
                        .build();
        Response response2 = httpClient.newCall(request2).execute();

        String responseBody = response2.body().string();
        JsonNode rootNode = OBJECT_MAPPER.readTree(responseBody);
        JsonNode dataNode = rootNode.path("data");

        JsonCodec<RoutingRule[]> responseCodec = JsonCodec.jsonCodec(RoutingRule[].class);
        RoutingRule[] routingRules = responseCodec.fromJson(dataNode.toString());

        assertThat(response.code()).isEqualTo(200);
        assertThat(routingRules[0].name()).isEqualTo("airflow");
        assertThat(routingRules[0].description()).isEqualTo("if query from airflow, route to adhoc group");
        assertThat(routingRules[0].priority()).isEqualTo(0);
        assertThat(routingRules[0].condition()).isEqualTo("request.getHeader(\"X-Trino-Source\") == \"JDBC\"");
        assertThat(routingRules[0].actions()).first().isEqualTo("result.put(\"routingGroup\", \"adhoc\")");

        // Revert back to old routing rules to avoid any test failures
        RoutingRule revertRoutingRules = new RoutingRule("airflow", "if query from airflow, route to etl group", 0, List.of("result.put(\"routingGroup\", \"etl\")"), "request.getHeader(\"X-Trino-Source\") == \"airflow\"");
        RequestBody requestBody3 = RequestBody.create(OBJECT_MAPPER.writeValueAsString(revertRoutingRules), MediaType.parse("application/json; charset=utf-8"));
        Request request3 = new Request.Builder()
                .url("http://localhost:" + routerPort + "/webapp/updateRoutingRules")
                .addHeader("Content-Type", "application/json")
                .post(requestBody3)
                .build();
        httpClient.newCall(request3).execute();
    }

    @Test
    void testCreateAndDeleteRoutingRuleAPI()
            throws Exception
    {
        RoutingRule newRule = new RoutingRule("api-created", "created through the API", 5, List.of("result.put(\"routingGroup\", \"etl\")"), "request.getHeader(\"X-Trino-Source\") == \"api\"");

        try {
            JsonNode created = postJson("/webapp/createRoutingRule", newRule);
            assertThat(created.path("code").asInt()).isEqualTo(200);
            assertThat(created.path("data").findValuesAsText("name")).contains("api-created");

            JsonNode duplicate = postJson("/webapp/createRoutingRule", newRule);
            assertThat(duplicate.path("code").asInt()).isNotEqualTo(200);
            assertThat(duplicate.path("msg").asText()).contains("already exists");

            RoutingRule invalidRule = new RoutingRule("api-invalid", "", 5, List.of("result.put(\"routingGroup\", \"etl\")"), "request.getHeader(");
            JsonNode invalid = postJson("/webapp/createRoutingRule", invalidRule);
            assertThat(invalid.path("code").asInt()).isNotEqualTo(200);
            assertThat(invalid.path("msg").asText()).contains("Invalid MVEL");

            JsonNode deleted = postJson("/webapp/deleteRoutingRule", Map.of("name", "api-created"));
            assertThat(deleted.path("code").asInt()).isEqualTo(200);
            assertThat(deleted.path("data").findValuesAsText("name")).doesNotContain("api-created");

            JsonNode deletedAgain = postJson("/webapp/deleteRoutingRule", Map.of("name", "api-created"));
            assertThat(deletedAgain.path("code").asInt()).isNotEqualTo(200);
            assertThat(deletedAgain.path("msg").asText()).contains("not found");

            JsonNode deleteWithoutName = postJson("/webapp/deleteRoutingRule", Map.of());
            assertThat(deleteWithoutName.path("code").asInt()).isNotEqualTo(200);
            assertThat(deleteWithoutName.path("msg").asText()).isEqualTo("Routing rule name is required");

            JsonNode deleteWithNullBody = postJson("/webapp/deleteRoutingRule", null);
            assertThat(deleteWithNullBody.path("code").asInt()).isNotEqualTo(200);
            assertThat(deleteWithNullBody.path("msg").asText()).isEqualTo("Routing rule name is required");
        }
        finally {
            // The API test config writes to a checked-in rules file, so never leave the rule behind
            postJson("/webapp/deleteRoutingRule", Map.of("name", "api-created"));
        }
    }

    private JsonNode postJson(String path, Object body)
            throws Exception
    {
        RequestBody requestBody = RequestBody.create(OBJECT_MAPPER.writeValueAsString(body), MediaType.parse("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url("http://localhost:" + routerPort + path)
                .post(requestBody)
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            assertThat(response.code()).isEqualTo(200);
            return OBJECT_MAPPER.readTree(response.body().string());
        }
    }

    @Test
    void testUIConfigurationAPI()
            throws Exception
    {
        Request request = new Request.Builder()
                .url("http://localhost:" + routerPort + "/webapp/getUIConfiguration")
                .get()
                .build();

        Response response = httpClient.newCall(request).execute();
        String responseBody = response.body().string();

        JsonNode rootNode = OBJECT_MAPPER.readTree(responseBody);
        JsonNode dataNode = rootNode.path("data");

        ObjectMapper objectMapper = new ObjectMapper();
        UIConfiguration uiConfiguration = objectMapper.readValue(dataNode.toString(), UIConfiguration.class);

        assertThat(response.code()).isEqualTo(200);
        assertThat(uiConfiguration.getDisablePages()).containsExactly("routing-rules");
    }
}
