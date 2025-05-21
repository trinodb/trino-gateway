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

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.TrinoContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.testcontainers.utility.MountableFile.forClasspathResource;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
final class TestNonExistentRoutingGroup
{
    private final OkHttpClient httpClient = new OkHttpClient();
    private TrinoContainer trino;
    int routerPort = 22000 + (int) (Math.random() * 1000);

    @BeforeAll
    void setup()
            throws Exception
    {
        trino = new TrinoContainer("trinodb/trino");
        trino.withCopyFileToContainer(forClasspathResource("trino-config.properties"), "/etc/trino/config.properties");
        trino.start();

        int backendPort = trino.getMappedPort(8080);

        // seed database with a default backend
        HaGatewayTestUtils.TestConfig testConfig =
                HaGatewayTestUtils.buildGatewayConfigAndSeedDb(routerPort, "test-config-template.yml");
        // Start Gateway
        String[] args = {testConfig.configFilePath()};
        HaGatewayLauncher.main(args);
        // Set up the backend with "adhoc" routing group
        HaGatewayTestUtils.setUpBackend(
                "trino1", "http://localhost:" + backendPort, "externalUrl", true, "adhoc", routerPort);
    }

    @Test
    void testNonExistentRoutingGroupError()
            throws Exception
    {
        // Send a request with a non-existent routing group
        RequestBody requestBody = RequestBody.create(
                "SELECT * from system.runtime.nodes",
                MediaType.parse("application/json; charset=utf-8"));

        Request request = new Request.Builder()
                .url("http://localhost:" + routerPort + "/v1/statement")
                .addHeader("X-Trino-User", "test")
                .addHeader("X-Trino-Routing-Group", "non_existent_group")
                .post(requestBody)
                .build();

        Response response = httpClient.newCall(request).execute();

        // Should get a 404 error
        assertThat(response.code()).isEqualTo(404);

        // Check the error message
        String responseBody = response.body().string();
        assertThat(responseBody).contains("The router group does not exist: non_existent_group");
    }

    @AfterAll
    void cleanup()
    {
        trino.close();
    }
}
