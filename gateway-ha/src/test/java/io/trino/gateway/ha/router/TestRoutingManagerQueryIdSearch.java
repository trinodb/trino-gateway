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
package io.trino.gateway.ha.router;

import com.sun.net.httpserver.HttpServer;
import io.trino.gateway.ha.config.ProxyBackendConfiguration;
import io.trino.gateway.ha.config.RoutingConfiguration;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

final class TestRoutingManagerQueryIdSearch
{
    @Test // regression test for https://github.com/trinodb/trino-gateway/issues/943
    void testFindBackendForQueryIdOnDeactivatedBackend()
            throws Exception
    {
        HttpServer server = startQueryServer();
        StochasticRoutingManager routingManager = null;
        try {
            String backendUrl = "http://localhost:" + server.getAddress().getPort();
            ProxyBackendConfiguration deactivated = backend(backendUrl, false);

            GatewayBackendManager backendManager = Mockito.mock(GatewayBackendManager.class);
            when(backendManager.getAllBackends()).thenReturn(List.of(deactivated));
            when(backendManager.getActiveBackends(any())).thenReturn(List.of());

            routingManager = new StochasticRoutingManager(backendManager, Mockito.mock(QueryHistoryManager.class), new RoutingConfiguration());
            assertThat(routingManager.findBackendForQueryId("20240101_000000_00003_ccccc")).isEqualTo(backendUrl);
        }
        finally {
            if (routingManager != null) {
                routingManager.shutdown();
            }
            server.stop(0);
        }
    }

    @Test
    void testFindBackendForQueryIdIsNotDelayedByUnresponsiveBackend()
            throws Exception
    {
        HttpServer server = startQueryServer();
        // Accepts connections but never answers, so a probe against it blocks until its read timeout
        ServerSocket unresponsive = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        StochasticRoutingManager routingManager = null;
        try {
            String unresponsiveUrl = "http://127.0.0.1:" + unresponsive.getLocalPort();
            String backendUrl = "http://localhost:" + server.getAddress().getPort();

            GatewayBackendManager backendManager = Mockito.mock(GatewayBackendManager.class);
            // The unresponsive backend is listed first
            when(backendManager.getAllBackends()).thenReturn(List.of(backend(unresponsiveUrl, true), backend(backendUrl, false)));
            when(backendManager.getActiveBackends(any())).thenReturn(List.of());

            routingManager = new StochasticRoutingManager(backendManager, Mockito.mock(QueryHistoryManager.class), new RoutingConfiguration());
            long start = System.nanoTime();
            assertThat(routingManager.findBackendForQueryId("20240101_000000_00004_ddddd")).isEqualTo(backendUrl);
            assertThat(System.nanoTime() - start).isLessThan(TimeUnit.SECONDS.toNanos(3));
        }
        finally {
            if (routingManager != null) {
                routingManager.shutdown();
            }
            unresponsive.close();
            server.stop(0);
        }
    }

    private static HttpServer startQueryServer()
            throws Exception
    {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/v1/query/", exchange -> {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        return server;
    }

    private static ProxyBackendConfiguration backend(String proxyTo, boolean active)
    {
        ProxyBackendConfiguration backend = new ProxyBackendConfiguration();
        backend.setName(proxyTo);
        backend.setProxyTo(proxyTo);
        backend.setActive(active);
        return backend;
    }
}
