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

import com.sun.net.httpserver.HttpServer;
import io.airlift.http.client.HttpClientConfig;
import io.airlift.http.client.jetty.JettyHttpClient;
import io.trino.gateway.ha.config.MonitorConfiguration;
import io.trino.gateway.ha.config.ProxyBackendConfiguration;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

final class TestClusterStatsPingMonitor
{
    @Test
    void pingPongAgainstRealServer()
            throws Exception
    {
        // Real HTTP server serving /v1/ping -> 200 "pong"
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/ping", exchange -> {
            byte[] body = "pong".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        int port = server.getAddress().getPort();

        try (JettyHttpClient client = new JettyHttpClient(new HttpClientConfig())) {
            ClusterStatsPingMonitor monitor = new ClusterStatsPingMonitor(client, new MonitorConfiguration());

            ProxyBackendConfiguration backend = new ProxyBackendConfiguration();
            backend.setName("live_cluster");
            backend.setProxyTo("http://127.0.0.1:" + port);

            // Server up -> HEALTHY
            assertThat(monitor.monitor(backend).trinoStatus()).isEqualTo(TrinoStatus.HEALTHY);

            // Server down -> UNHEALTHY
            server.stop(0);
            assertThat(monitor.monitor(backend).trinoStatus()).isEqualTo(TrinoStatus.UNHEALTHY);
        }
    }

    @Test
    void nonOkResponseIsUnhealthy()
            throws Exception
    {
        // Real HTTP server serving /v1/ping -> 500 (non-retryable)
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/ping", exchange -> {
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        server.start();
        int port = server.getAddress().getPort();

        try (JettyHttpClient client = new JettyHttpClient(new HttpClientConfig())) {
            ClusterStatsPingMonitor monitor = new ClusterStatsPingMonitor(client, new MonitorConfiguration());

            ProxyBackendConfiguration backend = new ProxyBackendConfiguration();
            backend.setName("error_cluster");
            backend.setProxyTo("http://127.0.0.1:" + port);

            assertThat(monitor.monitor(backend).trinoStatus()).isEqualTo(TrinoStatus.UNHEALTHY);
        }
        finally {
            server.stop(0);
        }
    }

    @Test
    void retryableStatusIsRetriedThenUnhealthy()
            throws Exception
    {
        // Real HTTP server serving /v1/ping -> 503 (retryable) on every request
        AtomicInteger requestCount = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/ping", exchange -> {
            requestCount.incrementAndGet();
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });
        server.start();
        int port = server.getAddress().getPort();

        try (JettyHttpClient client = new JettyHttpClient(new HttpClientConfig())) {
            MonitorConfiguration monitorConfiguration = new MonitorConfiguration();
            monitorConfiguration.setRetries(2);
            ClusterStatsPingMonitor monitor = new ClusterStatsPingMonitor(client, monitorConfiguration);

            ProxyBackendConfiguration backend = new ProxyBackendConfiguration();
            backend.setName("flaky_cluster");
            backend.setProxyTo("http://127.0.0.1:" + port);

            assertThat(monitor.monitor(backend).trinoStatus()).isEqualTo(TrinoStatus.UNHEALTHY);
            // Initial attempt + 2 retries
            assertThat(requestCount.get()).isEqualTo(3);
        }
        finally {
            server.stop(0);
        }
    }

    @Test
    void malformedProxyToIsUnhealthy()
            throws Exception
    {
        try (JettyHttpClient client = new JettyHttpClient(new HttpClientConfig())) {
            ClusterStatsPingMonitor monitor = new ClusterStatsPingMonitor(client, new MonitorConfiguration());

            ProxyBackendConfiguration backend = new ProxyBackendConfiguration();
            backend.setName("malformed_cluster");
            // Malformed URI: URI.create fails before the request is sent
            backend.setProxyTo("http://not a valid host");

            // Exception during request construction must be caught, not propagated
            assertThat(monitor.monitor(backend).trinoStatus()).isEqualTo(TrinoStatus.UNHEALTHY);
        }
    }
}
