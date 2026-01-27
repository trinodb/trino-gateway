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

import io.trino.gateway.ha.config.DataStoreConfiguration;
import io.trino.gateway.ha.config.RoutingConfiguration;
import io.trino.gateway.ha.config.ValkeyConfiguration;
import io.trino.gateway.ha.persistence.JdbcConnectionManager;
import org.junit.jupiter.api.Test;

import static io.trino.gateway.ha.TestingJdbcConnectionManager.createTestingJdbcConnectionManager;
import static io.trino.gateway.ha.TestingJdbcConnectionManager.dataStoreConfig;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class TestRoutingManagerNotFound
{
    private final RoutingManager routingManager;

    public TestRoutingManagerNotFound()
    {
        DataStoreConfiguration dataStoreConfig = dataStoreConfig();
        JdbcConnectionManager connectionManager = createTestingJdbcConnectionManager(dataStoreConfig);
        RoutingConfiguration routingConfiguration = new RoutingConfiguration();
        routingConfiguration.setDefaultRoutingGroup("default");

        GatewayBackendManager backendManager = new HaGatewayManager(connectionManager.getJdbi(), routingConfiguration);
        QueryHistoryManager historyManager = new HaQueryHistoryManager(connectionManager.getJdbi(), dataStoreConfig);
        DistributedCache distributedCache = new NoopDistributedCache();
        ValkeyConfiguration valkeyConfiguration = new ValkeyConfiguration();

        this.routingManager = new StochasticRoutingManager(backendManager, historyManager, routingConfiguration, distributedCache, valkeyConfiguration);
    }

    @Test
    void testNonExistentRoutingGroupThrowsNotFoundException()
    {
        // When requesting a non-existent routing group, an IllegalStateException should be thrown
        assertThatThrownBy(() -> routingManager.provideBackendConfiguration("non_existent_group", "user"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Number of active backends found zero");
    }
}
