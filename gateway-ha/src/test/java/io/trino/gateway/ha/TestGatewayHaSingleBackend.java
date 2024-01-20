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
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.testcontainers.containers.TrinoContainer;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(Lifecycle.PER_CLASS)
public class TestGatewayHaSingleBackend
{
    private final OkHttpClient httpClient = new OkHttpClient();
    private TrinoContainer trino;
    int routerPort = 21001 + (int) (Math.random() * 1000);

    @BeforeAll
    public void setup()
            throws Exception
    {
        trino = new TrinoContainer("trinodb/trino");
        trino.start();

        int backendPort = trino.getMappedPort(8080);

        // seed database
        HaGatewayTestUtils.TestConfig testConfig =
                HaGatewayTestUtils.buildGatewayConfigAndSeedDb(routerPort, "test-config-template.yml");
        // Start Gateway
        String[] args = {"server", testConfig.configFilePath()};
        HaGatewayLauncher.main(args);
        // Now populate the backend
        HaGatewayTestUtils.setUpBackend(
                "trino1", "http://localhost:" + backendPort, "externalUrl", true, "adhoc", routerPort);
    }

    @Test
    public void testRequestDelivery()
            throws Exception
    {
        RequestBody requestBody =
                RequestBody.create(MediaType.parse("application/json; charset=utf-8"), "SELECT 1");
        Request request =
                new Request.Builder()
                        .url("http://localhost:" + routerPort + "/v1/statement")
                        .addHeader("X-Trino-User", "test")
                        .post(requestBody)
                        .build();
        Response response = httpClient.newCall(request).execute();
        assertThat(response.body().string()).contains("nextUri");
    }

    @Test
    public void testBackendConfiguration()
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
        assertThat(backendConfiguration[0].getRoutingGroup()).isEqualTo("adhoc");
        assertThat(backendConfiguration[0].getExternalUrl()).isEqualTo("externalUrl");
    }

    @AfterAll
    public void cleanup()
    {
        trino.close();
    }
}
