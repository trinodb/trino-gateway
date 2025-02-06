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

import com.google.common.net.MediaType;
import io.airlift.http.client.HttpClient;
import io.airlift.http.client.HttpClientConfig;
import io.airlift.http.client.HttpStatus;
import io.airlift.http.client.jetty.JettyHttpClient;
import io.airlift.http.client.testing.TestingHttpClient;
import io.airlift.http.client.testing.TestingResponse;
import io.airlift.units.Duration;
import io.trino.gateway.ha.config.BackendStateConfiguration;
import io.trino.gateway.ha.config.MonitorConfiguration;
import io.trino.gateway.ha.config.ProxyBackendConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.TrinoContainer;

import java.util.function.Function;

import static java.util.concurrent.TimeUnit.SECONDS;
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
        trino.withCopyFileToContainer(forClasspathResource("trino-config-with-rmi.properties"), "/etc/trino/config.properties");
        trino.withCopyFileToContainer(forClasspathResource("jvm-with-rmi.config"), "/etc/trino/jvm.config");
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
        MonitorConfiguration monitorConfigurationWithTimeout = new MonitorConfiguration();
        monitorConfigurationWithTimeout.setQueryTimeout(new Duration(30, SECONDS));
        testClusterStatsMonitor(backendStateConfiguration -> new ClusterStatsJdbcMonitor(backendStateConfiguration, monitorConfigurationWithTimeout));
    }

    @Test
    void testJmxMonitor()
    {
        testClusterStatsMonitor(backendStateConfiguration -> new ClusterStatsJmxMonitor(new JettyHttpClient(new HttpClientConfig()), backendStateConfiguration));
    }

    @Test
    void testJmxMonitorWithBadRequest()
    {
        HttpClient client = new TestingHttpClient(_ -> TestingResponse
                .mockResponse(HttpStatus.BAD_REQUEST, MediaType.PLAIN_TEXT_UTF_8, "Bad Request"));

        testClusterStatsMonitorWithClient(client);
    }

    @Test
    void testJmxMonitorWithServerError()
    {
        HttpClient client = new TestingHttpClient(_ -> TestingResponse
                .mockResponse(HttpStatus.INTERNAL_SERVER_ERROR, MediaType.PLAIN_TEXT_UTF_8, "Internal Server Error"));

        testClusterStatsMonitorWithClient(client);
    }

    @Test
    void testJmxMonitorWithInvalidJson()
    {
        HttpClient client = new TestingHttpClient(_ -> TestingResponse
                .mockResponse(HttpStatus.OK, MediaType.JSON_UTF_8, "{invalid:json}"));

        testClusterStatsMonitorWithClient(client);
    }

    @Test
    void testJmxMonitorWithNetworkError()
    {
        HttpClient client = new TestingHttpClient(_ -> {
            throw new RuntimeException("Network error");
        });

        testClusterStatsMonitorWithClient(client);
    }

    private static void testClusterStatsMonitorWithClient(HttpClient client)
    {
        BackendStateConfiguration backendStateConfiguration = new BackendStateConfiguration();
        backendStateConfiguration.setUsername("test_user");
        ClusterStatsMonitor monitor = new ClusterStatsJmxMonitor(client, backendStateConfiguration);

        ProxyBackendConfiguration proxyBackend = new ProxyBackendConfiguration();
        proxyBackend.setProxyTo("http://localhost:8080");
        proxyBackend.setName("test_cluster");

        ClusterStats stats = monitor.monitor(proxyBackend);
        assertThat(stats.trinoStatus()).isEqualTo(TrinoStatus.UNHEALTHY);
    }

    @Test
    void testInfoApiMonitor()
    {
        MonitorConfiguration monitorConfigurationWithRetries = new MonitorConfiguration();
        monitorConfigurationWithRetries.setRetries(10);
        testClusterStatsMonitor(_ -> new ClusterStatsInfoApiMonitor(new JettyHttpClient(new HttpClientConfig()), new MonitorConfiguration()));
        testClusterStatsMonitor(_ -> new ClusterStatsInfoApiMonitor(new JettyHttpClient(new HttpClientConfig()), monitorConfigurationWithRetries));
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
        assertThat(stats.trinoStatus()).isEqualTo(TrinoStatus.HEALTHY);
    }
}
