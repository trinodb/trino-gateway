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

import com.google.common.collect.ImmutableMap;
import io.airlift.http.client.HttpClientConfig;
import io.airlift.http.client.jetty.JettyHttpClient;
import io.trino.gateway.ha.config.BackendStateConfiguration;
import io.trino.gateway.ha.config.MonitorConfiguration;
import io.trino.gateway.ha.config.ProxyBackendConfiguration;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;

import static io.trino.gateway.ha.clustermonitor.ClusterStatsMetricsMonitor.ACTIVE_NODE_COUNT_METRIC;
import static io.trino.gateway.ha.clustermonitor.ClusterStatsMetricsMonitor.LEGACY_ACTIVE_NODE_COUNT_METRIC;
import static org.assertj.core.api.Assertions.assertThat;

final class TestClusterStatsMetricsMonitor
{
    private static final String QUERY_METRICS =
            """
            trino_execution_name_QueryManager_RunningQueries 0
            trino_execution_name_QueryManager_QueuedQueries 0
            """;

    @Test
    void testDefaultAcceptsLegacyActiveNodeCount()
            throws Exception
    {
        // Trino 476 and earlier expose the active node count under its previous name
        assertThat(monitor(new MonitorConfiguration(), LEGACY_ACTIVE_NODE_COUNT_METRIC + " 1\n"))
                .isEqualTo(TrinoStatus.HEALTHY);
    }

    @Test
    void testLegacyNameAcceptsCurrentActiveNodeCount()
            throws Exception
    {
        // A configuration written for Trino 476 and earlier keeps working with Trino 477 and later
        MonitorConfiguration configuration = new MonitorConfiguration();
        configuration.setMetricMinimumValues(ImmutableMap.of(LEGACY_ACTIVE_NODE_COUNT_METRIC, 1f));
        assertThat(monitor(configuration, ACTIVE_NODE_COUNT_METRIC + " 1\n"))
                .isEqualTo(TrinoStatus.HEALTHY);
    }

    @Test
    void testAliasAppliesMinimumValue()
            throws Exception
    {
        MonitorConfiguration configuration = new MonitorConfiguration();
        configuration.setMetricMinimumValues(ImmutableMap.of(ACTIVE_NODE_COUNT_METRIC, 2f));
        assertThat(monitor(configuration, LEGACY_ACTIVE_NODE_COUNT_METRIC + " 1\n"))
                .isEqualTo(TrinoStatus.UNHEALTHY);
    }

    @Test
    void testMissingActiveNodeCount()
            throws Exception
    {
        assertThat(monitor(new MonitorConfiguration(), ""))
                .isEqualTo(TrinoStatus.UNHEALTHY);
    }

    private static TrinoStatus monitor(MonitorConfiguration monitorConfiguration, String activeNodeCountMetrics)
            throws Exception
    {
        BackendStateConfiguration backendStateConfiguration = new BackendStateConfiguration();
        backendStateConfiguration.setUsername("probe");
        monitorConfiguration.setRetries(0);
        try (MockWebServer server = new MockWebServer();
                JettyHttpClient httpClient = new JettyHttpClient(new HttpClientConfig())) {
            server.enqueue(new MockResponse().setBody(QUERY_METRICS + activeNodeCountMetrics));
            server.start();

            ProxyBackendConfiguration backend = new ProxyBackendConfiguration();
            backend.setName("mock");
            backend.setProxyTo(server.url("").toString().replaceAll("/$", ""));
            ClusterStatsMonitor monitor = new ClusterStatsMetricsMonitor(httpClient, backendStateConfiguration, monitorConfiguration);
            return monitor.monitor(backend).trinoStatus();
        }
    }
}
