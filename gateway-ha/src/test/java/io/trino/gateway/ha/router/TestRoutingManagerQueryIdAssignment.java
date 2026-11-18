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

import io.trino.gateway.ha.config.ProxyBackendConfiguration;
import io.trino.gateway.ha.config.RoutingConfiguration;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class TestRoutingManagerQueryIdAssignment
{
    private static final String QUERY_ID = "20261106_180000_00001_abcde";
    private static final String DEFAULT_BACKEND = "http://default-backend:8080";

    private GatewayBackendManager backendManager;
    private QueryHistoryManager queryHistoryManager;
    private StochasticRoutingManager routingManager;

    @BeforeEach
    void setUp()
    {
        backendManager = mock(GatewayBackendManager.class);
        queryHistoryManager = mock(QueryHistoryManager.class);
        RoutingConfiguration routingConfiguration = new RoutingConfiguration();
        when(backendManager.getActiveBackends(routingConfiguration.getDefaultRoutingGroup()))
                .thenReturn(List.of(backend("default", DEFAULT_BACKEND)));
        routingManager = new StochasticRoutingManager(backendManager, queryHistoryManager, routingConfiguration);
    }

    @AfterEach
    void tearDown()
    {
        routingManager.shutdown();
    }

    @Test
    void testFirstAssignmentIsKept()
    {
        assertThat(routingManager.assignBackendForQueryId(QUERY_ID, "http://backend-1:8080")).isTrue();
        assertThat(routingManager.assignBackendForQueryId(QUERY_ID, "http://backend-2:8080")).isFalse();

        assertThat(routingManager.findBackendForQueryId(QUERY_ID)).isEqualTo("http://backend-1:8080");
    }

    @Test
    void testRepeatedAssignmentToSameBackend()
    {
        assertThat(routingManager.assignBackendForQueryId(QUERY_ID, "http://backend-1:8080")).isTrue();
        assertThat(routingManager.assignBackendForQueryId(QUERY_ID, "http://backend-1:8080")).isTrue();

        assertThat(routingManager.findBackendForQueryId(QUERY_ID)).isEqualTo("http://backend-1:8080");
    }

    @Test
    void testAssignmentReplacesEarlierLookup()
    {
        // No history and no backend knows the query, so the lookup falls back to the default backend
        assertThat(routingManager.findBackendForQueryId(QUERY_ID)).isEqualTo(DEFAULT_BACKEND);

        assertThat(routingManager.assignBackendForQueryId(QUERY_ID, "http://backend-1:8080")).isTrue();

        assertThat(routingManager.findBackendForQueryId(QUERY_ID)).isEqualTo("http://backend-1:8080");
    }

    @Test
    void testLookupUsesQueryHistory()
    {
        when(queryHistoryManager.getBackendForQueryId(QUERY_ID)).thenReturn("http://backend-1:8080");

        assertThat(routingManager.findBackendForQueryId(QUERY_ID)).isEqualTo("http://backend-1:8080");
    }

    @Test
    void testSearchFindsSingleBackend()
            throws IOException
    {
        try (MockWebServer knowsQuery = new MockWebServer();
                MockWebServer doesNotKnowQuery = new MockWebServer()) {
            knowsQuery.enqueue(new MockResponse().setResponseCode(200));
            doesNotKnowQuery.enqueue(new MockResponse().setResponseCode(404));
            String knowsQueryUrl = proxyTo(knowsQuery);
            when(backendManager.getAllBackends()).thenReturn(List.of(
                    backend("knows-query", knowsQueryUrl),
                    backend("does-not-know-query", proxyTo(doesNotKnowQuery))));

            assertThat(routingManager.findBackendForQueryId(QUERY_ID)).isEqualTo(knowsQueryUrl);
        }
    }

    @Test
    void testSearchIgnoresQueryReportedByMultipleBackends()
            throws IOException
    {
        try (MockWebServer first = new MockWebServer();
                MockWebServer second = new MockWebServer()) {
            first.enqueue(new MockResponse().setResponseCode(200));
            second.enqueue(new MockResponse().setResponseCode(200));
            when(backendManager.getAllBackends()).thenReturn(List.of(
                    backend("first", proxyTo(first)),
                    backend("second", proxyTo(second))));

            assertThat(routingManager.findBackendForQueryId(QUERY_ID)).isEqualTo(DEFAULT_BACKEND);
        }
    }

    @Test
    void testSearchWaitsForAllBackendsTogether()
            throws IOException
    {
        // More slow backends than lookup threads, so waiting for each one in turn would take twice the timeout
        List<MockWebServer> slowServers = new ArrayList<>();
        try (MockWebServer knowsQuery = new MockWebServer()) {
            knowsQuery.enqueue(new MockResponse().setResponseCode(200));
            String knowsQueryUrl = proxyTo(knowsQuery);
            List<ProxyBackendConfiguration> backends = new ArrayList<>();
            backends.add(backend("knows-query", knowsQueryUrl));
            for (int i = 0; i < 6; i++) {
                MockWebServer slowServer = new MockWebServer();
                slowServers.add(slowServer);
                slowServer.enqueue(new MockResponse().setResponseCode(200).setHeadersDelay(30, SECONDS));
                backends.add(backend("slow-" + i, proxyTo(slowServer)));
            }
            when(backendManager.getAllBackends()).thenReturn(backends);

            long start = System.nanoTime();
            assertThat(routingManager.findBackendForQueryId(QUERY_ID)).isEqualTo(knowsQueryUrl);
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(8));
        }
        finally {
            for (MockWebServer slowServer : slowServers) {
                slowServer.close();
            }
        }
    }

    private static String proxyTo(MockWebServer server)
    {
        String url = server.url("/").toString();
        return url.substring(0, url.length() - 1);
    }

    private static ProxyBackendConfiguration backend(String name, String proxyTo)
    {
        ProxyBackendConfiguration backend = new ProxyBackendConfiguration();
        backend.setName(name);
        backend.setProxyTo(proxyTo);
        return backend;
    }
}
