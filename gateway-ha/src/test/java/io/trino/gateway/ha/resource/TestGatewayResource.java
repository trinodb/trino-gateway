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

import io.airlift.json.JsonCodec;
import io.trino.gateway.ha.HaGatewayLauncher;
import io.trino.gateway.ha.HaGatewayTestUtils;
import io.trino.gateway.ha.clustermonitor.ClusterStats;
import io.trino.gateway.ha.clustermonitor.TrinoStatus;
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
final class TestGatewayResource
{
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final PostgreSQLContainer postgresql = createPostgreSqlContainer();
    private final OkHttpClient httpClient = new OkHttpClient();
    final int routerPort = 22001 + (int) (Math.random() * 1000);

    @BeforeAll
    void setup()
            throws Exception
    {
        postgresql.start();
        File configFile = HaGatewayTestUtils.buildGatewayConfig(
                postgresql, routerPort, "test-config-no-monitor-template.yml");
        HaGatewayLauncher.main(new String[] {configFile.getAbsolutePath()});
        addBackend("activate-test", false);
        addBackend("deactivate-test", true);
    }

    @Test
    void testActivateBackendSetsPendingInHealthCache()
            throws IOException
    {
        Request request = new Request.Builder()
                .url("http://localhost:%s/gateway/backend/activate/activate-test".formatted(routerPort))
                .post(RequestBody.create("", JSON))
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            assertThat(response.isSuccessful()).isTrue();
        }

        assertThat(getBackendState("activate-test").trinoStatus()).isEqualTo(TrinoStatus.PENDING);
    }

    @Test
    void testDeactivateBackendSetsUnhealthyInHealthCache()
            throws IOException
    {
        Request request = new Request.Builder()
                .url("http://localhost:%s/gateway/backend/deactivate/deactivate-test".formatted(routerPort))
                .post(RequestBody.create("", JSON))
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            assertThat(response.isSuccessful()).isTrue();
        }

        assertThat(getBackendState("deactivate-test").trinoStatus()).isEqualTo(TrinoStatus.UNKNOWN);
    }

    @Test
    void testActivateBackendDoesNotUpdateHealthCacheOnError()
            throws IOException
    {
        Request request = new Request.Builder()
                .url("http://localhost:%s/gateway/backend/activate/nonexistent".formatted(routerPort))
                .post(RequestBody.create("", JSON))
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            assertThat(response.code()).isEqualTo(404);
        }

        Request stateRequest = new Request.Builder()
                .url("http://localhost:%s/api/public/backends/nonexistent/state".formatted(routerPort))
                .get()
                .build();
        try (Response response = httpClient.newCall(stateRequest).execute()) {
            assertThat(response.code()).isEqualTo(404);
        }
    }

    @Test
    void testDeactivateBackendDoesNotUpdateHealthCacheOnError()
            throws IOException
    {
        Request request = new Request.Builder()
                .url("http://localhost:%s/gateway/backend/deactivate/nonexistent".formatted(routerPort))
                .post(RequestBody.create("", JSON))
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            assertThat(response.code()).isEqualTo(404);
        }

        Request stateRequest = new Request.Builder()
                .url("http://localhost:%s/api/public/backends/nonexistent/state".formatted(routerPort))
                .get()
                .build();
        try (Response response = httpClient.newCall(stateRequest).execute()) {
            assertThat(response.code()).isEqualTo(404);
        }
    }

    private void addBackend(String name, boolean active)
            throws IOException
    {
        String body = ("{\"name\":\"%s\",\"proxyTo\":\"http://localhost:9999\","
                + "\"externalUrl\":\"http://localhost:9999\","
                + "\"routingGroup\":\"adhoc\",\"active\":%s}")
                .formatted(name, active);
        Request request = new Request.Builder()
                .url("http://localhost:%s/gateway/backend/modify/add".formatted(routerPort))
                .post(RequestBody.create(body, JSON))
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            assertThat(response.isSuccessful()).isTrue();
        }
    }

    private ClusterStats getBackendState(String name)
            throws IOException
    {
        Request request = new Request.Builder()
                .url("http://localhost:%s/api/public/backends/%s/state".formatted(routerPort, name))
                .get()
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            return JsonCodec.jsonCodec(ClusterStats.class).fromJson(response.body().string());
        }
    }
}
