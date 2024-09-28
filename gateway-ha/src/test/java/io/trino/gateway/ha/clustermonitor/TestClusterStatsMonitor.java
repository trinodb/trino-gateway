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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.TrinoContainer;

import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.testcontainers.utility.MountableFile.forClasspathResource;

@TestInstance(PER_CLASS)
final class TestClusterStatsMonitor
{
    private TrinoContainer trino;

    @BeforeAll
    void setUp()
    {
        trino = new TrinoContainer("trinodb/trino");
        trino.withCopyFileToContainer(forClasspathResource("trino-config.properties"), "/etc/trino/config.properties");
        trino.start();
    }

    @AfterAll
    void setup()
    {
        trino.close();
    }

    @Test
    void testHttpMonitor()
    {
        testClusterStatsMonitor(ClusterStatsHttpMonitor::new);
    }

    @Test
    void testJdbcMonitor()
    {
        testClusterStatsMonitor(backendStateConfiguration -> new ClusterStatsJdbcMonitor(backendStateConfiguration, new MonitorConfiguration()));
    }

    @Test
    void testInfoApiMonitor()
    {
        MonitorConfiguration monitorConfigurationWithRetries = new MonitorConfiguration();
        monitorConfigurationWithRetries.setRetries(10);
        testClusterStatsMonitor(ignored -> new ClusterStatsInfoApiMonitor(new JettyHttpClient(new HttpClientConfig()), new MonitorConfiguration()));
        testClusterStatsMonitor(ignored -> new ClusterStatsInfoApiMonitor(new JettyHttpClient(new HttpClientConfig()), monitorConfigurationWithRetries));
    }

    private void testClusterStatsMonitor(Function<BackendStateConfiguration, ClusterStatsMonitor> monitorFactory)
    {
        BackendStateConfiguration backendStateConfiguration = new BackendStateConfiguration();
        backendStateConfiguration.setUsername("test_user");
        ClusterStatsMonitor monitor = monitorFactory.apply(backendStateConfiguration);

        ProxyBackendConfiguration proxyBackend = new ProxyBackendConfiguration();
        proxyBackend.setProxyTo("http://localhost:" + trino.getMappedPort(8080));
        proxyBackend.setName("test_cluster");

        ClusterStats stats = monitor.monitor(proxyBackend);
        assertThat(stats.clusterId()).isEqualTo("test_cluster");
        assertThat(stats.healthy()).isTrue();
    }
}
