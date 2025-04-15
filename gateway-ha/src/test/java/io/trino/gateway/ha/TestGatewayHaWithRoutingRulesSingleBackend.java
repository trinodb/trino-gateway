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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.trino.gateway.ha.config.ProxyBackendConfiguration;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.trino.TrinoContainer;

import java.io.File;

import static io.trino.gateway.ha.util.TestcontainersUtils.createPostgreSqlContainer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.testcontainers.utility.MountableFile.forClasspathResource;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
final class TestGatewayHaWithRoutingRulesSingleBackend
{
    private final OkHttpClient httpClient = new OkHttpClient();
    private TrinoContainer trino;
    private final PostgreSQLContainer postgresql = createPostgreSqlContainer();
    int routerPort = 21001 + (int) (Math.random() * 1000);

    @BeforeAll
    void setup()
            throws Exception
    {
        trino = new TrinoContainer("trinodb/trino");
        trino.withCopyFileToContainer(forClasspathResource("trino-config.properties"), "/etc/trino/config.properties");
        trino.start();
        postgresql.start();

        int backendPort = trino.getMappedPort(8080);

        // seed database
        File testConfigFile =
                HaGatewayTestUtils.buildGatewayConfig(postgresql, routerPort, "test-config-with-routing-template.yml");
        // Start Gateway
        System.setProperty("config", testConfigFile.getAbsolutePath());
        HaGatewayLauncher.main(new String[] {});
        // Now populate the backend
        HaGatewayTestUtils.setUpBackend(
                "trino1", "http://localhost:" + backendPort, "externalUrl", true, "system", routerPort);
    }

    @Test
    void testRequestDelivery()
            throws Exception
    {
        RequestBody requestBody =
                RequestBody.create("SELECT * from system.runtime.nodes", MediaType.parse("application/json; charset=utf-8"));
        Request request =
                new Request.Builder()
                        .url("http://localhost:" + routerPort + "/v1/statement")
                        .addHeader("X-Trino-User", "test")
                        .post(requestBody)
                        .build();
        Response response = httpClient.newCall(request).execute();
        assertThat(response.body().string()).contains("nextUri");
    }

    // Do not allow trino gateway to fall back to the adhoc routing group if the desired backend is not found
    @Test
    void testVerifyNoAdhoc()
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

        assertThat(backendConfiguration).hasSize(1);
        assertThat(backendConfiguration[0].isActive()).isTrue();
        assertThat(backendConfiguration[0].getRoutingGroup()).isNotEqualTo("adhoc");
    }

    @AfterAll
    void cleanup()
    {
        trino.close();
    }
}
