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

import io.trino.gateway.ha.cache.NoopDistributedCache;
import io.trino.gateway.ha.cache.QueryCacheManager;
import io.trino.gateway.ha.clustermonitor.TrinoStatus;
import io.trino.gateway.ha.config.DataStoreConfiguration;
import io.trino.gateway.ha.config.DatabaseCacheConfiguration;
import io.trino.gateway.ha.config.ProxyBackendConfiguration;
import io.trino.gateway.ha.config.RoutingConfiguration;
import io.trino.gateway.ha.persistence.JdbcConnectionManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import static io.trino.gateway.ha.TestingJdbcConnectionManager.createTestingJdbcConnectionManager;
import static io.trino.gateway.ha.TestingJdbcConnectionManager.dataStoreConfig;
import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(Lifecycle.PER_CLASS)
final class TestStochasticRoutingManager
{
    RoutingManager haRoutingManager;
    GatewayBackendManager backendManager;
    QueryHistoryManager historyManager;

    @BeforeAll
    void setUp()
    {
        DataStoreConfiguration dataStoreConfig = dataStoreConfig();
        JdbcConnectionManager connectionManager = createTestingJdbcConnectionManager(dataStoreConfig);
        RoutingConfiguration routingConfiguration = new RoutingConfiguration();
        backendManager = new HaGatewayManager(connectionManager.getJdbi(), routingConfiguration, new DatabaseCacheConfiguration());
        historyManager = new HaQueryHistoryManager(connectionManager.getJdbi(), dataStoreConfig);

        // Create QueryCacheManager with loader
        QueryCacheManager.QueryCacheLoader loader = new QueryCacheManager.QueryCacheLoader()
        {
            @Override
            public String loadBackendFromDatabase(String queryId)
            {
                return historyManager.getBackendForQueryId(queryId);
            }

            @Override
            public String loadRoutingGroupFromDatabase(String queryId)
            {
                return historyManager.getRoutingGroupForQueryId(queryId);
            }

            @Override
            public String loadExternalUrlFromDatabase(String queryId)
            {
                return historyManager.getExternalUrlForQueryId(queryId);
            }
        };
        QueryCacheManager queryCacheManager = new QueryCacheManager(new NoopDistributedCache(), loader);

        haRoutingManager = new StochasticRoutingManager(backendManager, historyManager, routingConfiguration, queryCacheManager);
    }

    @Test
    void testAddMockBackends()
    {
        String groupName = "test_group";
        int numBackends = 5;
        String backend;
        for (int i = 0; i < numBackends; i++) {
            backend = groupName + i;
            ProxyBackendConfiguration proxyBackend = new ProxyBackendConfiguration();
            proxyBackend.setActive(true);
            proxyBackend.setRoutingGroup(groupName);
            proxyBackend.setName(backend);
            proxyBackend.setProxyTo(backend + ".trino.example.com");
            proxyBackend.setExternalUrl("trino.example.com");
            backendManager.addBackend(proxyBackend);
            //set backend as healthy to start with
            haRoutingManager.updateBackEndHealth(backend, TrinoStatus.HEALTHY);
        }

        //Keep only 1st backend as healthy, mark all the others as unhealthy
        assertThat(backendManager.getAllActiveBackends()).isNotEmpty();

        for (int i = 1; i < numBackends; i++) {
            backend = groupName + i;
            haRoutingManager.updateBackEndHealth(backend, TrinoStatus.UNHEALTHY);
        }

        assertThat(haRoutingManager.provideBackendConfiguration(groupName, "").getProxyTo())
                .isEqualTo("test_group0.trino.example.com");
    }
}
