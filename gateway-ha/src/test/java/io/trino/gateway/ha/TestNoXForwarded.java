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

import io.airlift.json.JsonCodec;
import io.trino.client.QueryResults;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.TrinoContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.testcontainers.utility.MountableFile.forClasspathResource;

@TestInstance(Lifecycle.PER_CLASS)
final class TestNoXForwarded
{
    private final OkHttpClient httpClient = new OkHttpClient();
    private TrinoContainer trino;
    private PostgreSQLContainer postgresql;
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

        postgresql = new PostgreSQLContainer("postgres:16");
        postgresql.start();

        HaGatewayTestUtils.TestConfig testConfig =
                HaGatewayTestUtils.buildGatewayConfig(routerPort, "test-config-without-x-forwarded-template.yml", postgresql);
        // Start Gateway
        String[] args = {testConfig.configFilePath()};
        HaGatewayLauncher.main(args);
        // Now populate the backend
        HaGatewayTestUtils.setUpBackend(
                "trino1", "http://localhost:" + backendPort, "externalUrl", true, "adhoc", routerPort);
    }

    @Test
    void testRequestDelivery()
            throws Exception
    {
        RequestBody requestBody =
                RequestBody.create("SELECT 1", MediaType.parse("application/json; charset=utf-8"));
        Request request =
                new Request.Builder()
                        .url("http://localhost:" + routerPort + "/v1/statement")
                        .addHeader("X-Trino-User", "test")
                        .post(requestBody)
                        .build();
        Response response = httpClient.newCall(request).execute();
        JsonCodec<QueryResults> responseCodec = JsonCodec.jsonCodec(QueryResults.class);
        QueryResults queryResults = responseCodec.fromJson(response.body().string());

        assertThat(queryResults.getNextUri().getHost()).isEqualTo("localhost");
        assertThat(queryResults.getNextUri().getPort()).isEqualTo(backendPort);
    }

    @AfterAll
    void cleanup()
    {
        trino.close();
    }
}
