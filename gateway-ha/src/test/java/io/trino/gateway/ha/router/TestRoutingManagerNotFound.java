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

import io.trino.gateway.ha.config.RoutingConfiguration;
import io.trino.gateway.ha.persistence.JdbcConnectionManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import static io.trino.gateway.ha.TestingJdbcConnectionManager.createTestingJdbcConnectionManager;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@TestInstance(Lifecycle.PER_CLASS)
final class TestRoutingManagerNotFound
{
    private RoutingManager routingManager;

    @BeforeAll
    void setUp()
    {
        JdbcConnectionManager connectionManager = createTestingJdbcConnectionManager();
        RoutingConfiguration routingConfiguration = new RoutingConfiguration();
        routingConfiguration.setDefaultRoutingGroup("default");
        GatewayBackendManager backendManager = new HaGatewayManager(connectionManager.getJdbi(), routingConfiguration);
        QueryHistoryManager historyManager = new HaQueryHistoryManager(connectionManager.getJdbi(), false);
        routingManager = new StochasticRoutingManager(backendManager, historyManager, routingConfiguration);
    }

    @Test
    void testNonExistentRoutingGroupThrowsNotFoundException()
    {
        // When requesting a non-existent routing group, an IllegalStateException should be thrown
        assertThatThrownBy(() -> routingManager.provideClusterForRoutingGroup("non_existent_group", "user"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Number of active backends found zero");
    }
}
