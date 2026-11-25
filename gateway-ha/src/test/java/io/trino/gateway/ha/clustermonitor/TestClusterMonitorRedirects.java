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
import io.trino.gateway.ha.config.BackendStateConfiguration;
import io.trino.gateway.ha.config.MonitorConfiguration;
import io.trino.gateway.ha.config.ProxyBackendConfiguration;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

final class TestClusterMonitorRedirects
{
    @Test
    void testInfoApiMonitorDoesNotFollowRedirects()
            throws IOException
    {
        assertRedirectNotFollowed(_ -> new ClusterStatsInfoApiMonitor(new JettyHttpClient(new HttpClientConfig()), new MonitorConfiguration()), 1, TrinoStatus.UNHEALTHY);
    }

    @Test
    void testJmxMonitorDoesNotFollowRedirects()
            throws IOException
    {
        // The JMX monitor requests two MBeans
        assertRedirectNotFollowed(configuration -> new ClusterStatsJmxMonitor(new JettyHttpClient(new HttpClientConfig()), configuration), 2, TrinoStatus.UNHEALTHY);
    }

    @Test
    void testMetricsMonitorDoesNotFollowRedirects()
            throws IOException
    {
        assertRedirectNotFollowed(configuration -> new ClusterStatsMetricsMonitor(new JettyHttpClient(new HttpClientConfig()), configuration, new MonitorConfiguration()), 1, TrinoStatus.UNHEALTHY);
    }

    @Test
    void testHttpMonitorDoesNotFollowRedirects()
            throws IOException
    {
        // The HTTP monitor tries the login resource of the current Web UI and then the one of older
        // releases. Both answer with a redirect, so the login fails, which it reports as unknown.
        assertRedirectNotFollowed(ClusterStatsHttpMonitor::new, 2, TrinoStatus.UNKNOWN);
    }

    private static void assertRedirectNotFollowed(Function<BackendStateConfiguration, ClusterStatsMonitor> monitorFactory, int expectedRequests, TrinoStatus expectedStatus)
            throws IOException
    {
        try (MockWebServer server = new MockWebServer()) {
            // Every response redirects back to the same server, so a monitor that follows
            // redirects sends more requests than expected
            for (int i = 0; i < 10; i++) {
                server.enqueue(new MockResponse()
                        .setResponseCode(302)
                        .setHeader("Location", server.url("/redirected").toString()));
            }

            BackendStateConfiguration configuration = new BackendStateConfiguration();
            configuration.setUsername("test_user");
            ProxyBackendConfiguration backend = new ProxyBackendConfiguration();
            backend.setName("test_cluster");
            backend.setProxyTo(server.url("").toString().replaceAll("/$", ""));

            ClusterStats stats = monitorFactory.apply(configuration).monitor(backend);

            assertThat(stats.trinoStatus()).isEqualTo(expectedStatus);
            assertThat(server.getRequestCount()).isEqualTo(expectedRequests);
        }
    }
}
