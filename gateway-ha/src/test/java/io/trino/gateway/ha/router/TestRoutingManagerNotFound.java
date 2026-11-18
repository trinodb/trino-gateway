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
import org.jdbi.v3.core.Jdbi;
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
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@TestInstance(Lifecycle.PER_CLASS)
final class TestRoutingManagerNotFound
{
    @Container
    private static final PostgreSQLContainer POSTGRESQL = createPostgreSqlContainer();

    private final MockWebServer backend = new MockWebServer();
    private String backendUrl;
    // The default routing group has no Trino cluster
    private RoutingManager routingManager;
    // The default routing group has one healthy Trino cluster, backed by the mock web server
    private RoutingManager unknownQueryIdRoutingManager;

    @BeforeAll
    void setUp()
            throws IOException
    {
        DataStoreConfiguration dataStoreConfig = dataStoreConfig(POSTGRESQL);
        Jdbi jdbi = new JdbcConnectionManager(dataStoreConfig).getJdbi();

        this.routingManager = createRoutingManager(jdbi, dataStoreConfig, "default");

        backend.start();
        backendUrl = "http://localhost:" + backend.getPort();
        RoutingConfiguration routingConfiguration = new RoutingConfiguration();
        routingConfiguration.setDefaultRoutingGroup("unknown-query-id");
        GatewayBackendManager backendManager = new HaGatewayManager(jdbi, routingConfiguration, new DatabaseCacheConfiguration());
        QueryHistoryManager historyManager = new HaQueryHistoryManager(jdbi, dataStoreConfig, new WriteBufferConfiguration());
        this.unknownQueryIdRoutingManager = new StochasticRoutingManager(backendManager, historyManager, routingConfiguration);

        ProxyBackendConfiguration proxyBackend = new ProxyBackendConfiguration();
        proxyBackend.setActive(true);
        proxyBackend.setRoutingGroup("unknown-query-id");
        proxyBackend.setName("unknown-query-id-cluster");
        proxyBackend.setProxyTo(backendUrl);
        proxyBackend.setExternalUrl(backendUrl);
        backendManager.addBackend(proxyBackend);
        unknownQueryIdRoutingManager.updateBackEndHealth("unknown-query-id-cluster", TrinoStatus.HEALTHY);
    }

    @AfterAll
    void tearDown()
            throws IOException
    {
        backend.shutdown();
    }

    @Test
    void testNonExistentRoutingGroupThrowsNotFoundException()
    {
        assertThatThrownBy(() -> routingManager.provideBackendConfiguration("non_existent_group", "user"))
                .isInstanceOf(NoHealthyBackendException.class)
                .hasMessage("No healthy backend found for routing group [non_existent_group] or default routing group [default]");
    }

    @Test
    void testUnknownQueryIdUsesDefaultRoutingGroup()
    {
        assertThat(unknownQueryIdRoutingManager.findBackendForQueryId("20261118_000000_00000_abcde")).isEqualTo(backendUrl);
        // The Trino clusters are not asked about the unknown query id
        assertThat(backend.getRequestCount()).isZero();
    }

    private static RoutingManager createRoutingManager(Jdbi jdbi, DataStoreConfiguration dataStoreConfig, String defaultRoutingGroup)
    {
        RoutingConfiguration routingConfiguration = new RoutingConfiguration();
        routingConfiguration.setDefaultRoutingGroup(defaultRoutingGroup);
        GatewayBackendManager backendManager = new HaGatewayManager(jdbi, routingConfiguration, new DatabaseCacheConfiguration());
        QueryHistoryManager historyManager = new HaQueryHistoryManager(jdbi, dataStoreConfig, new WriteBufferConfiguration());
        return new StochasticRoutingManager(backendManager, historyManager, routingConfiguration);
    }
}
