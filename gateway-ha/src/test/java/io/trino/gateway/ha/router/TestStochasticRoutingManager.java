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
import io.trino.gateway.ha.persistence.JdbcConnectionManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static io.trino.gateway.ha.TestingJdbcConnectionManager.dataStoreConfig;
import static io.trino.gateway.ha.util.TestcontainersUtils.createPostgreSqlContainer;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@TestInstance(Lifecycle.PER_CLASS)
final class TestStochasticRoutingManager
{
    @Container
    private static final PostgreSQLContainer POSTGRESQL = createPostgreSqlContainer();

    RoutingManager haRoutingManager;
    GatewayBackendManager backendManager;
    QueryHistoryManager historyManager;

    @BeforeAll
    void setUp()
    {
        DataStoreConfiguration dataStoreConfig = dataStoreConfig(POSTGRESQL);
        JdbcConnectionManager connectionManager = new JdbcConnectionManager(dataStoreConfig);
        RoutingConfiguration routingConfiguration = new RoutingConfiguration();
        backendManager = new HaGatewayManager(connectionManager.getJdbi(), routingConfiguration, new DatabaseCacheConfiguration());
        historyManager = new HaQueryHistoryManager(connectionManager.getJdbi(), dataStoreConfig);
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
            ProxyBackendConfiguration proxyBackend = new ProxyBackendConfiguration();
            proxyBackend.setActive(true);
            proxyBackend.setRoutingGroup(groupName);
            proxyBackend.setName(backend);
            proxyBackend.setProxyTo(backend + ".trino.example.com");
            proxyBackend.setExternalUrl("trino.example.com");
            backendManager.addBackend(proxyBackend);
            // set backend as healthy to start with
            haRoutingManager.updateBackEndHealth(backend, TrinoStatus.HEALTHY);
        }

        // Keep only 1st backend as healthy, mark all the others as unhealthy
        assertThat(backendManager.getAllActiveBackends()).isNotEmpty();

        for (int i = 1; i < numBackends; i++) {
            backend = groupName + i;
            haRoutingManager.updateBackEndHealth(backend, TrinoStatus.UNHEALTHY);
        }

        assertThat(haRoutingManager.provideBackendConfiguration(groupName, "").getProxyTo())
                .isEqualTo("test_group0.trino.example.com");
    }

    @Test
    void testIsBackendActive()
    {
        ProxyBackendConfiguration active = oauthBackend("oauth-active", "http://oauth-active:8080", true);
        // Not checked yet, as on a freshly started instance
        assertThat(haRoutingManager.isBackendActive("http://oauth-active:8080")).isTrue();
        // The health status does not matter
        for (TrinoStatus status : TrinoStatus.values()) {
            haRoutingManager.updateBackEndHealth(active.getName(), status);
            assertThat(haRoutingManager.isBackendActive("http://oauth-active:8080")).isTrue();
        }

        ProxyBackendConfiguration deactivated = oauthBackend("oauth-deactivated", "http://oauth-deactivated:8080", false);
        haRoutingManager.updateBackEndHealth(deactivated.getName(), TrinoStatus.HEALTHY);
        assertThat(haRoutingManager.isBackendActive("http://oauth-deactivated:8080")).isFalse();

        // Unknown backend
        assertThat(haRoutingManager.isBackendActive("http://oauth-does-not-exist:8080")).isFalse();

        // A proxyTo carrying a base path still matches its scheme://authority pin
        oauthBackend("oauth-path", "http://oauth-path:8080/gateway", true);
        assertThat(haRoutingManager.isBackendActive("http://oauth-path:8080")).isTrue();

        // Host comparison is case-insensitive
        oauthBackend("oauth-mixed", "http://OAuth-Mixed:8080", true);
        assertThat(haRoutingManager.isBackendActive("http://oauth-mixed:8080")).isTrue();

        // Any active backend with the address counts, even if an inactive one shares it
        oauthBackend("oauth-shared-inactive", "http://oauth-shared:8080", false);
        oauthBackend("oauth-shared-active", "http://oauth-shared:8080", true);
        assertThat(haRoutingManager.isBackendActive("http://oauth-shared:8080")).isTrue();
    }

    private ProxyBackendConfiguration oauthBackend(String name, String proxyTo, boolean active)
    {
        ProxyBackendConfiguration backend = new ProxyBackendConfiguration();
        backend.setActive(active);
        backend.setRoutingGroup("oauth-group");
        backend.setName(name);
        backend.setProxyTo(proxyTo);
        backend.setExternalUrl("https://ext.example");
        backendManager.addBackend(backend);
        return backend;
    }

    @Test
    void testFindInactiveUnhealthyBackendName()
    {
        // Inactive and unhealthy -> out of service. A proxyTo carrying a base path and a mixed-case
        // host still match the scheme://authority the proxy fails against.
        addBackend("down-a", "http://Down-A:8080/gateway", false);
        haRoutingManager.updateBackEndHealth("down-a", TrinoStatus.UNHEALTHY);
        assertThat(haRoutingManager.findInactiveUnhealthyBackendName("http://down-a:8080")).contains("down-a");

        // A deactivated backend that is no longer tracked reports UNKNOWN, which is not healthy.
        addBackend("down-b", "http://down-b:8080", false);
        haRoutingManager.updateBackEndHealth("down-b", TrinoStatus.UNKNOWN);
        assertThat(haRoutingManager.findInactiveUnhealthyBackendName("http://down-b:8080")).contains("down-b");

        // Active backends are serving traffic, healthy or not.
        addBackend("up-a", "http://up-a:8080", true);
        haRoutingManager.updateBackEndHealth("up-a", TrinoStatus.UNHEALTHY);
        assertThat(haRoutingManager.findInactiveUnhealthyBackendName("http://up-a:8080")).isEmpty();

        // Inactive but still reporting healthy is not treated as scaled down.
        addBackend("down-healthy", "http://down-healthy:8080", false);
        haRoutingManager.updateBackEndHealth("down-healthy", TrinoStatus.HEALTHY);
        assertThat(haRoutingManager.findInactiveUnhealthyBackendName("http://down-healthy:8080")).isEmpty();

        // An active backend sharing the address means the cluster is still served.
        addBackend("shared-down", "http://shared:8080", false);
        haRoutingManager.updateBackEndHealth("shared-down", TrinoStatus.UNHEALTHY);
        addBackend("shared-up", "http://shared:8080", true);
        haRoutingManager.updateBackEndHealth("shared-up", TrinoStatus.HEALTHY);
        assertThat(haRoutingManager.findInactiveUnhealthyBackendName("http://shared:8080")).isEmpty();

        assertThat(haRoutingManager.findInactiveUnhealthyBackendName("http://does-not-exist:8080")).isEmpty();
    }

    private void addBackend(String name, String proxyTo, boolean active)
    {
        ProxyBackendConfiguration backend = new ProxyBackendConfiguration();
        backend.setName(name);
        backend.setProxyTo(proxyTo);
        backend.setExternalUrl("https://ext.example");
        backend.setRoutingGroup("inactive-lookup-group");
        backend.setActive(active);
        backendManager.addBackend(backend);
    }
}
