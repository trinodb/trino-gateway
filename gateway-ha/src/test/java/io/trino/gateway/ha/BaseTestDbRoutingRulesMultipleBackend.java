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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import io.trino.gateway.ha.config.ProxyBackendConfiguration;
import io.trino.gateway.ha.persistence.dao.RoutingRule;
import io.trino.gateway.ha.persistence.dao.RoutingRuleEngine;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.TrinoContainer;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.List;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.testcontainers.utility.MountableFile.forClasspathResource;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class BaseTestDbRoutingRulesMultipleBackend
{
    private static final MediaType MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");

    private TrinoContainer adhocTrino;
    private TrinoContainer systemTrino;
    private final JdbcDatabaseContainer<?> database;
    private int adhocTrinoMappedPort;
    private int systemTrinoMappedPort;

    final int routerPort = 20000 + (int) (Math.random() * 1000);

    private final OkHttpClient httpClient = new OkHttpClient();

    public BaseTestDbRoutingRulesMultipleBackend(JdbcDatabaseContainer<?> container)
    {
        this.database = requireNonNull(container, "container is null");
        this.database.start();
    }

    @BeforeAll
    void setup()
            throws Exception
    {
        adhocTrino = new TrinoContainer("trinodb/trino");
        adhocTrino.withCopyFileToContainer(forClasspathResource("trino-config.properties"), "/etc/trino/config.properties");
        adhocTrino.start();
        systemTrino = new TrinoContainer("trinodb/trino");
        systemTrino.withCopyFileToContainer(forClasspathResource("trino-config.properties"), "/etc/trino/config.properties");
        systemTrino.start();
        database.start();

        adhocTrinoMappedPort = adhocTrino.getMappedPort(8080);
        systemTrinoMappedPort = systemTrino.getMappedPort(8080);

        File testConfigFile =
                HaGatewayTestUtils.buildGatewayConfig(database, routerPort, "test-config-with-db-routing-rules.yaml", database.getDriverClassName());

        String[] args = {testConfigFile.getAbsolutePath()};
        HaGatewayLauncher.main(args);

        HaGatewayTestUtils.setUpBackend(
                "trino1", "http://localhost:" + adhocTrinoMappedPort, "externalUrl", true, "adhoc", routerPort);
        HaGatewayTestUtils.setUpBackend(
                "trino2", "http://localhost:" + systemTrinoMappedPort, "externalUrl", true, "system", routerPort);

        getRules().forEach(routingRule -> applyRule(routingRule, Operation.CREATE));
        Thread.sleep(2000); //ensure rules are loaded
    }

    private List<RoutingRule> getRules()
    {
        return ImmutableList.of(new RoutingRule(
                "system-group",
                "capture queries to system catalog",
                0,
                "trinoQueryProperties.getCatalogs().contains(\"system\")",
                ImmutableList.of("result.put(RulesRoutingGroupSelector.RESULTS_ROUTING_GROUP_KEY, \"system\")"),
                RoutingRuleEngine.MVEL));
    }

    private void applyRule(RoutingRule routingRule, Operation operation)
    {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            RequestBody requestBody = RequestBody.create(objectMapper.writeValueAsString(routingRule), MediaType.parse("application/json; charset=utf-8"));

            String path = switch (operation) {
                case CREATE -> "/webapp/createRoutingRule";
                case UPDATE -> "/webapp/updateRoutingRules";
                case DELETE -> "/webapp/deleteRoutingRules";
            };

            Request request = new Request.Builder()
                    .url("http://localhost:" + routerPort + path)
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody)
                    .build();
            Response response = httpClient.newCall(request).execute();
            if (!response.isSuccessful()) {
                throw new RuntimeException("Rule creation failed with response code " + response.code() + " and body " + response.body().string());
            }
        }
        catch (JsonProcessingException e) {
            throw new RuntimeException("Could not serialize rule as JSON", e);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testQueryDeliveryToMultipleRoutingGroups()
            throws Exception
    {
        // Default request should be routed to adhoc backend
        submitQueryAndCheckDelivery("SELECT 1", adhocTrinoMappedPort);

        submitQueryAndCheckDelivery("SELECT * from system.runtime.nodes", systemTrinoMappedPort);
    }

    @Test
    void testRoutingWithUpdates()
            throws Exception
    {
        submitQueryAndCheckDelivery("SELECT * from system.runtime.nodes", systemTrinoMappedPort);

        //update rule to only capture system.jdbc queries
        applyRule(new RoutingRule(
                "system-group",
                "capture queries to system catalog",
                0,
                "trinoQueryProperties.getCatalogSchemas().contains(\"system.jdbc\")",
                ImmutableList.of("result.put(RulesRoutingGroupSelector.RESULTS_ROUTING_GROUP_KEY, \"system\")"),
                RoutingRuleEngine.MVEL), Operation.UPDATE);
        Thread.sleep(2000);

        submitQueryAndCheckDelivery("SELECT * from system.runtime.nodes", adhocTrinoMappedPort);
        submitQueryAndCheckDelivery("SELECT * from system.jdbc.tables", systemTrinoMappedPort);

        Request deleteRequest = new Request.Builder()
                .url("http://localhost:" + routerPort + "/webapp/deleteRoutingRule/system-group")
                .delete()
                .build();
        Response response = httpClient.newCall(deleteRequest).execute();
        assertThat(response.code()).isIn(200, 202, 204);
        Thread.sleep(2000);
        submitQueryAndCheckDelivery("SELECT * from system.jdbc.tables", adhocTrinoMappedPort);

        getRules().forEach(routingRule -> applyRule(routingRule, Operation.CREATE)); // Reset for other tests
        Thread.sleep(2000);
        submitQueryAndCheckDelivery("SELECT * from system.runtime.nodes", systemTrinoMappedPort);
    }

    @Test
    void testBackendConfiguration()
            throws Exception
    {
        Request request = new Request.Builder()
                .url("http://localhost:" + routerPort + "/entity/GATEWAY_BACKEND")
                .method("GET", null)
                .build();
        Response response = httpClient.newCall(request).execute();

        final ObjectMapper objectMapper = new ObjectMapper();
        ProxyBackendConfiguration[] backendConfiguration =
                objectMapper.readValue(response.body().string(), ProxyBackendConfiguration[].class);

        assertThat(backendConfiguration).isNotNull();
        assertThat(backendConfiguration).hasSize(2);
        assertThat(backendConfiguration[0].isActive()).isTrue();
        assertThat(backendConfiguration[1].isActive()).isTrue();
        assertThat(backendConfiguration[0].getRoutingGroup()).isEqualTo("adhoc");
        assertThat(backendConfiguration[1].getRoutingGroup()).isEqualTo("system");
        assertThat(backendConfiguration[0].getExternalUrl()).isEqualTo("externalUrl");
        assertThat(backendConfiguration[1].getExternalUrl()).isEqualTo("externalUrl");
    }

    private void submitQueryAndCheckDelivery(String queryText, int expectedBackendPort)
            throws Exception
    {
        // Intended for use with addXForwardedHeaders: false, so that the backend host is used for nextUri
        ObjectMapper objectMapper = new ObjectMapper();
        RequestBody requestBody = RequestBody.create(queryText, MEDIA_TYPE);
        Request request =
                new Request.Builder()
                        .url("http://localhost:" + routerPort + "/v1/statement")
                        .addHeader("X-Trino-User", "test")
                        .post(requestBody)
                        .build();
        Response response = httpClient.newCall(request).execute();
        String responseBody = response.body().string();

        HashMap<String, String> results = objectMapper.readValue(responseBody, HashMap.class);
        URI nextUri = URI.create(results.get("nextUri"));
        assertThat(nextUri.getPort()).isEqualTo(expectedBackendPort);
    }

    @AfterAll
    void cleanup()
    {
        adhocTrino.stop();
        systemTrino.stop();
    }

    enum Operation
    {
        CREATE,
        UPDATE,
        DELETE
    }
}
