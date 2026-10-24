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

import com.google.common.net.HostAndPort;
import com.sun.net.httpserver.HttpServer;
import io.trino.gateway.ha.config.DataStoreConfiguration;
import io.trino.gateway.ha.config.DatabaseCacheConfiguration;
import io.trino.gateway.ha.config.ProxyBackendConfiguration;
import io.trino.gateway.ha.config.RoutingConfiguration;
import io.trino.gateway.ha.persistence.JdbcConnectionManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static io.trino.gateway.ha.TestingJdbcConnectionManager.createTestingPostgresContainer;
import static io.trino.gateway.ha.TestingJdbcConnectionManager.dataStoreConfig;
import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(Lifecycle.PER_CLASS)
final class TestRoutingManagerSearchAllBackends
{
    private static final String QUERY_ID = "20260903_120000_00001_abcde";

    private HttpServer queryOwner;
    private HttpServer otherBackend;
    private final List<HttpServer> hungBackends = new ArrayList<>();
    private final CountDownLatch releaseHungBackends = new CountDownLatch(1);
    private BaseRoutingManager routingManager;
    private PostgreSQLContainer postgres;
    private String queryOwnerUrl;
    private String otherBackendUrl;

    @BeforeAll
    void setUp()
            throws IOException
    {
        // The backend that actually knows the query answers 200. It responds with a small delay,
        // which is what a real cluster does and what makes the outcome independent of whether the
        // probe happens to have finished by the time its result is inspected.
        queryOwner = startBackend(200, 150, null);
        // Any other backend does not know the query.
        otherBackend = startBackend(404, 0, null);
        queryOwnerUrl = urlOf(queryOwner);
        otherBackendUrl = urlOf(otherBackend);
        // Backends that accept the request but do not answer until the test releases them. The results are
        // read in the order the probes finish, so the query owner has to be found while these are still
        // outstanding. Reading the results in submission order would spend the shared deadline waiting on them.
        for (int i = 0; i < 4; i++) {
            hungBackends.add(startBackend(404, 0, releaseHungBackends));
        }

        postgres = createTestingPostgresContainer();
        DataStoreConfiguration dataStoreConfig = dataStoreConfig(postgres);
        JdbcConnectionManager connectionManager = new JdbcConnectionManager(dataStoreConfig);
        RoutingConfiguration routingConfiguration = new RoutingConfiguration();
        routingConfiguration.setDefaultRoutingGroup("default");

        GatewayBackendManager backendManager = new HaGatewayManager(connectionManager.getJdbi(), routingConfiguration, new DatabaseCacheConfiguration());
        QueryHistoryManager historyManager = new HaQueryHistoryManager(connectionManager.getJdbi(), dataStoreConfig);

        // Only the fallback candidate is in the default routing group, so returning the query
        // owner cannot be an accident of the fallback picking it.
        backendManager.addBackend(backend("query-owner", queryOwnerUrl, "owners"));
        backendManager.addBackend(backend("other-backend", otherBackendUrl, "default"));
        for (int i = 0; i < hungBackends.size(); i++) {
            backendManager.addBackend(backend("hung-backend-" + i, urlOf(hungBackends.get(i)), "default"));
        }

        routingManager = new StochasticRoutingManager(backendManager, historyManager, routingConfiguration);
    }

    @AfterAll
    void tearDown()
    {
        // The handlers of the hung backends run on the dispatcher thread of their server, and stop() waits for it
        releaseHungBackends.countDown();
        if (routingManager != null) {
            routingManager.shutdown();
        }
        if (queryOwner != null) {
            queryOwner.stop(0);
        }
        if (otherBackend != null) {
            otherBackend.stop(0);
        }
        hungBackends.forEach(server -> server.stop(0));
        if (postgres != null) {
            postgres.close();
        }
    }

    @Test
    void testFindsBackendThatOwnsTheQuery()
    {
        // Goes through the cache, as production lookups do
        assertThat(routingManager.findBackendForQueryId(QUERY_ID))
                .isEqualTo(queryOwnerUrl);
    }

    private static HttpServer startBackend(int statusCode, long delayMillis, CountDownLatch release)
            throws IOException
    {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/v1/query/", exchange -> {
            try {
                if (release != null) {
                    release.await();
                }
                if (delayMillis > 0) {
                    Thread.sleep(delayMillis);
                }
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.sendResponseHeaders(statusCode, -1);
            exchange.close();
        });
        server.start();
        return server;
    }

    private static String urlOf(HttpServer server)
    {
        InetSocketAddress address = server.getAddress();
        return "http://" + HostAndPort.fromParts(address.getAddress().getHostAddress(), address.getPort());
    }

    private static ProxyBackendConfiguration backend(String name, String proxyTo, String routingGroup)
    {
        ProxyBackendConfiguration backend = new ProxyBackendConfiguration();
        backend.setActive(true);
        backend.setName(name);
        backend.setProxyTo(proxyTo);
        backend.setExternalUrl(proxyTo);
        backend.setRoutingGroup(routingGroup);
        return backend;
    }
}
