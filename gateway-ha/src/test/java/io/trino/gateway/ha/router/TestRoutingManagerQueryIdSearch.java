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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
            String backendUrl = url(server);
            routingManager = createRoutingManager(List.of(backend(backendUrl, false)));
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
            String backendUrl = url(server);

            // The unresponsive backend is listed first
            routingManager = createRoutingManager(List.of(backend(unresponsiveUrl, true), backend(backendUrl, false)));
            long start = System.nanoTime();
            assertThat(routingManager.findBackendForQueryId("20240101_000000_00004_ddddd")).isEqualTo(backendUrl);
            assertThat(System.nanoTime() - start).isLessThan(TimeUnit.SECONDS.toNanos(4));
        }
        finally {
            if (routingManager != null) {
                routingManager.shutdown();
            }
            unresponsive.close();
            server.stop(0);
        }
    }

    @Test
    void testFindBackendForQueryIdIsNotStarvedByManyUnresponsiveBackends()
            throws Exception
    {
        HttpServer server = startQueryServer();
        ServerSocket unresponsive = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        StochasticRoutingManager routingManager = null;
        try {
            String backendUrl = url(server);

            // Every probe must start right away, even when unresponsive backends outnumber a typical thread pool
            List<ProxyBackendConfiguration> backends = new ArrayList<>(
                    Collections.nCopies(20, backend("http://127.0.0.1:" + unresponsive.getLocalPort(), true)));
            backends.add(backend(backendUrl, false));

            routingManager = createRoutingManager(backends);
            long start = System.nanoTime();
            assertThat(routingManager.findBackendForQueryId("20240101_000000_00005_eeeee")).isEqualTo(backendUrl);
            assertThat(System.nanoTime() - start).isLessThan(TimeUnit.SECONDS.toNanos(4));
        }
        finally {
            if (routingManager != null) {
                routingManager.shutdown();
            }
            unresponsive.close();
            server.stop(0);
        }
    }

    @Test
    void testFindBackendForQueryIdDoesNotCacheMiss()
            throws Exception
    {
        AtomicInteger status = new AtomicInteger(410);
        HttpServer server = startQueryServer(status);
        StochasticRoutingManager routingManager = null;
        try {
            String backendUrl = url(server);
            String queryId = "20240101_000000_00006_fffff";

            // An unrelated active backend must not be returned, or cached, as the holder of the query
            routingManager = createRoutingManager(List.of(backend(backendUrl, false)));
            assertThat(routingManager.findBackendForQueryId(queryId)).isNull();

            status.set(200);
            assertThat(routingManager.findBackendForQueryId(queryId)).isEqualTo(backendUrl);
        }
        finally {
            if (routingManager != null) {
                routingManager.shutdown();
            }
            server.stop(0);
        }
    }

    @Test
    void testFindBackendForQueryIdDoesNotTreatAuthenticationRejectionAsMatch()
            throws Exception
    {
        HttpServer unauthorized = startQueryServer(new AtomicInteger(401));
        HttpServer forbidden = startQueryServer(new AtomicInteger(403));
        // Answers after the rejections above so that they are seen first
        HttpServer holder = startQueryServer(new AtomicInteger(200), 500);
        StochasticRoutingManager routingManager = null;
        try {
            List<ProxyBackendConfiguration> rejecting = List.of(backend(url(unauthorized), true), backend(url(forbidden), true));
            routingManager = createRoutingManager(rejecting);
            assertThat(routingManager.findBackendForQueryId("20240101_000000_00007_ggggg")).isNull();
            routingManager.shutdown();

            List<ProxyBackendConfiguration> backends = new ArrayList<>(rejecting);
            backends.add(backend(url(holder), false));
            routingManager = createRoutingManager(backends);
            assertThat(routingManager.findBackendForQueryId("20240101_000000_00008_hhhhh")).isEqualTo(url(holder));
        }
        finally {
            if (routingManager != null) {
                routingManager.shutdown();
            }
            unauthorized.stop(0);
            forbidden.stop(0);
            holder.stop(0);
        }
    }

    private static StochasticRoutingManager createRoutingManager(List<ProxyBackendConfiguration> backends)
    {
        GatewayBackendManager backendManager = Mockito.mock(GatewayBackendManager.class);
        when(backendManager.getAllBackends()).thenReturn(backends);
        when(backendManager.getActiveBackends(any())).thenReturn(List.of(backend("http://unrelated-active-backend.example.com", true)));
        return new StochasticRoutingManager(backendManager, Mockito.mock(QueryHistoryManager.class), new RoutingConfiguration());
    }

    private static HttpServer startQueryServer()
            throws Exception
    {
        return startQueryServer(new AtomicInteger(200));
    }

    private static HttpServer startQueryServer(AtomicInteger status)
            throws Exception
    {
        return startQueryServer(status, 0);
    }

    private static HttpServer startQueryServer(AtomicInteger status, long delayMillis)
            throws Exception
    {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/v1/query/", exchange -> {
            try {
                Thread.sleep(delayMillis);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.sendResponseHeaders(status.get(), -1);
            exchange.close();
        });
        server.start();
        return server;
    }

    private static String url(HttpServer server)
    {
        return "http://localhost:" + server.getAddress().getPort();
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
