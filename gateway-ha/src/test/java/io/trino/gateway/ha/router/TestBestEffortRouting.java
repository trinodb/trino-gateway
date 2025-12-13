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
import io.trino.gateway.ha.config.ProxyBackendConfiguration;
import io.trino.gateway.ha.config.RoutingConfiguration;
import io.trino.gateway.ha.persistence.JdbcConnectionManager;
import org.junit.jupiter.api.Test;

import static io.trino.gateway.ha.TestingJdbcConnectionManager.createTestingJdbcConnectionManager;
import static org.assertj.core.api.Assertions.assertThat;

final class TestBestEffortRouting
{
    @Test
    void testBestEffortRoutingEnabledAllUnhealthy()
    {
        JdbcConnectionManager connectionManager = createTestingJdbcConnectionManager();
        RoutingConfiguration routingConfiguration = new RoutingConfiguration();
        routingConfiguration.setBestEffortRouting(true);
        GatewayBackendManager backendMgr = new HaGatewayManager(connectionManager.getJdbi(), routingConfiguration);
        RoutingManager rm = new StochasticRoutingManager(backendMgr, new HaQueryHistoryManager(connectionManager.getJdbi(), false), routingConfiguration);

        String group = "adhoc";
        addActiveBackend(backendMgr, group, "trino-1");
        addActiveBackend(backendMgr, group, "trino-2");

        rm.updateBackEndHealth("trino-1", TrinoStatus.UNHEALTHY);
        rm.updateBackEndHealth("trino-2", TrinoStatus.UNHEALTHY);

        ProxyBackendConfiguration selected = rm.provideBackendConfiguration(group, "user");
        assertThat(selected.getName()).isIn("trino-1", "trino-2");
        assertThat(selected.getRoutingGroup()).isEqualTo(group);
    }

    @Test
    void testFallsBackWhenAllUnhealthyInGroup()
    {
        JdbcConnectionManager connectionManager = createTestingJdbcConnectionManager();
        RoutingConfiguration routingConfiguration = new RoutingConfiguration();
        routingConfiguration.setBestEffortRouting(true);
        routingConfiguration.setDefaultRoutingGroup("adhoc");
        GatewayBackendManager backendMgr = new HaGatewayManager(connectionManager.getJdbi(), routingConfiguration);
        RoutingManager rm = new StochasticRoutingManager(backendMgr, new HaQueryHistoryManager(connectionManager.getJdbi(), false), routingConfiguration);

        // Non-default group with all unhealthy
        String vipGroup = "vip";
        addActiveBackend(backendMgr, vipGroup, "vip-1");
        addActiveBackend(backendMgr, vipGroup, "vip-2");
        rm.updateBackEndHealth("vip-1", TrinoStatus.UNHEALTHY);
        rm.updateBackEndHealth("vip-2", TrinoStatus.UNHEALTHY);

        // Default group with one healthy and one unhealthy
        addActiveBackend(backendMgr, "adhoc", "adhoc-1");
        addActiveBackend(backendMgr, "adhoc", "adhoc-2");
        rm.updateBackEndHealth("adhoc-1", TrinoStatus.HEALTHY);
        rm.updateBackEndHealth("adhoc-2", TrinoStatus.UNHEALTHY);

        ProxyBackendConfiguration selected = rm.provideBackendConfiguration(vipGroup, "user");
        assertThat(selected.getRoutingGroup()).isEqualTo("adhoc");
        assertThat(selected.getName()).isEqualTo("adhoc-1");
    }

    private static void addActiveBackend(GatewayBackendManager mgr, String group, String name)
    {
        ProxyBackendConfiguration backend = new ProxyBackendConfiguration();
        backend.setActive(true);
        backend.setRoutingGroup(group);
        backend.setName(name);
        backend.setProxyTo(name + ".trino.example.com");
        backend.setExternalUrl("trino.example.com");
        mgr.addBackend(backend);
    }
}
