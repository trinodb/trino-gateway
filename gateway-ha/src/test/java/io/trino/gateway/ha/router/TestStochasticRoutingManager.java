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
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response.Status;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import static io.trino.gateway.ha.TestingJdbcConnectionManager.createTestingJdbcConnectionManager;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@TestInstance(Lifecycle.PER_CLASS)
final class TestStochasticRoutingManager
{
    RoutingManager haRoutingManager;
    GatewayBackendManager backendManager;
    QueryHistoryManager historyManager;

    @BeforeAll
    void setUp()
    {
        JdbcConnectionManager connectionManager = createTestingJdbcConnectionManager();
        RoutingConfiguration routingConfiguration = new RoutingConfiguration();
        backendManager = new HaGatewayManager(connectionManager.getJdbi(), routingConfiguration);
        historyManager = new HaQueryHistoryManager(connectionManager.getJdbi(), false);
        haRoutingManager = new StochasticRoutingManager(backendManager, historyManager, routingConfiguration);
    }

    @Test
    void testAddMockBackends()
    {
        String groupName = "test_group";
        int numBackends = 5;
        String backend;
        for (int i = 0; i < numBackends; i++) {
            backend = groupName + i;
            ProxyBackendConfiguration proxyBackend = createBackend(
                    backend,
                    groupName,
                    true,
                    backend + ".trino.example.com",
                    "trino.example.com");
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

        assertThat(haRoutingManager.provideBackendConfiguration(groupName, "", false).getProxyTo())
                .isEqualTo("test_group0.trino.example.com");
    }

    @Test
    void testStrictRoutingException()
    {
        ProxyBackendConfiguration inactiveBackend = createBackend(
                "inactive-backend",
                "inactive-group",
                false,
                "inactive.trino.example.com",
                "https://inactive.example");
        backendManager.addBackend(inactiveBackend);

        assertNotFoundForIsolation("inactive-group");

        ProxyBackendConfiguration unhealthyBackend = createBackend(
                "unhealthy-backend",
                "unhealthy-group",
                true,
                "unhealthy.trino.example.com",
                "https://unhealthy.example");
        backendManager.addBackend(unhealthyBackend);
        haRoutingManager.updateBackEndHealth(unhealthyBackend.getName(), TrinoStatus.UNHEALTHY);

        assertNotFoundForIsolation("unhealthy-group");

        assertNotFoundForIsolation("missing-group");
    }

    private void assertNotFoundForIsolation(String routingGroup)
    {
        assertThatThrownBy(() -> haRoutingManager.provideBackendConfiguration(routingGroup, "user", true))
                .isInstanceOfSatisfying(WebApplicationException.class, exception ->
                        assertThat(exception.getResponse().getStatus()).isEqualTo(Status.NOT_FOUND.getStatusCode()));
    }

    private static ProxyBackendConfiguration createBackend(
            String name,
            String routingGroup,
            boolean active,
            String proxyTo,
            String externalUrl)
    {
        ProxyBackendConfiguration backend = new ProxyBackendConfiguration();
        backend.setName(name);
        backend.setRoutingGroup(routingGroup);
        backend.setActive(active);
        backend.setProxyTo(proxyTo);
        backend.setExternalUrl(externalUrl);
        return backend;
    }
}
