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
package io.trino.gateway.ha.clustermonitor;

import io.airlift.http.client.HttpClientConfig;
import io.airlift.http.client.jetty.JettyHttpClient;
import io.airlift.json.JsonMapperProvider;
import io.trino.gateway.ha.config.MonitorConfiguration;
import io.trino.gateway.ha.config.ProxyBackendConfiguration;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

final class TestClusterStatsInfoApiMonitor
{
    @Test
    void testCoordinatorId()
            throws IOException
    {
        // Trino 477 and later report the coordinator ID
        ClusterStats stats = monitor("{\"starting\":false,\"coordinatorId\":\"krphu\"}");
        assertThat(stats.trinoStatus()).isEqualTo(TrinoStatus.HEALTHY);
        assertThat(stats.coordinatorId()).isEqualTo("krphu");
        // The backend state endpoints, including the public one, do not publish it
        assertThat(new JsonMapperProvider().get().writeValueAsString(stats)).doesNotContain("coordinatorId");
    }

    @Test
    void testNoCoordinatorId()
            throws IOException
    {
        // Trino 476 and older
        ClusterStats stats = monitor("{\"starting\":false}");
        assertThat(stats.trinoStatus()).isEqualTo(TrinoStatus.HEALTHY);
        assertThat(stats.coordinatorId()).isNull();
    }

    private static ClusterStats monitor(String serverInfo)
            throws IOException
    {
        try (MockWebServer server = new MockWebServer(); JettyHttpClient client = new JettyHttpClient(new HttpClientConfig())) {
            server.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody(serverInfo));
            server.start();
            ProxyBackendConfiguration backend = new ProxyBackendConfiguration();
            backend.setName("test_cluster");
            backend.setProxyTo(server.url("/").toString());
            return new ClusterStatsInfoApiMonitor(client, new MonitorConfiguration()).monitor(backend);
        }
    }
}
