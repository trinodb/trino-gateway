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

import io.trino.gateway.ha.clustermonitor.TrinoStatus;
import io.trino.gateway.ha.config.DataStoreConfiguration;
import io.trino.gateway.ha.config.DatabaseCacheConfiguration;
import io.trino.gateway.ha.config.ProxyBackendConfiguration;
import io.trino.gateway.ha.config.RoutingConfiguration;
import io.trino.gateway.ha.config.WriteBufferConfiguration;
import io.trino.gateway.ha.persistence.JdbcConnectionManager;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.IOException;

import static io.trino.gateway.ha.TestingJdbcConnectionManager.dataStoreConfig;
import static io.trino.gateway.ha.util.TestcontainersUtils.createPostgreSqlContainer;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@TestInstance(Lifecycle.PER_CLASS)
final class TestRoutingManagerUnknownQueryId
{
    @Container
    private static final PostgreSQLContainer POSTGRESQL = createPostgreSqlContainer();

    private final MockWebServer backend = new MockWebServer();
    private RoutingManager routingManager;
    private String backendUrl;

    @BeforeAll
    void setUp()
            throws IOException
    {
        backend.start();
        backendUrl = "http://localhost:" + backend.getPort();

        DataStoreConfiguration dataStoreConfig = dataStoreConfig(POSTGRESQL);
        JdbcConnectionManager connectionManager = new JdbcConnectionManager(dataStoreConfig);
        RoutingConfiguration routingConfiguration = new RoutingConfiguration();
        routingConfiguration.setDefaultRoutingGroup("default");
        GatewayBackendManager backendManager = new HaGatewayManager(connectionManager.getJdbi(), routingConfiguration, new DatabaseCacheConfiguration());
        QueryHistoryManager historyManager = new HaQueryHistoryManager(connectionManager.getJdbi(), dataStoreConfig, new WriteBufferConfiguration());
        routingManager = new StochasticRoutingManager(backendManager, historyManager, routingConfiguration);

        ProxyBackendConfiguration proxyBackend = new ProxyBackendConfiguration();
        proxyBackend.setActive(true);
        proxyBackend.setRoutingGroup("default");
        proxyBackend.setName("default-cluster");
        proxyBackend.setProxyTo(backendUrl);
        proxyBackend.setExternalUrl(backendUrl);
        backendManager.addBackend(proxyBackend);
        routingManager.updateBackEndHealth("default-cluster", TrinoStatus.HEALTHY);
    }

    @AfterAll
    void tearDown()
            throws IOException
    {
        backend.shutdown();
    }

    @Test
    void testUnknownQueryIdUsesDefaultRoutingGroup()
            throws InterruptedException
    {
        assertThat(routingManager.findBackendForQueryId("20261118_000000_00000_abcde")).isEqualTo(backendUrl);
        // The Trino clusters are not asked about the unknown query id
        assertThat(backend.takeRequest(1, SECONDS)).isNull();
    }
}
