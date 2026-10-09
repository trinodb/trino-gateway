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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    void testUnavailableGroupFallbackDisabledRejectsGroupWithoutBackends()
    {
        RoutingConfiguration noFallbackConfig = new RoutingConfiguration();
        noFallbackConfig.setUnavailableGroupFallbackEnabled(false);
        RoutingManager noFallbackRoutingManager = new StochasticRoutingManager(backendManager, historyManager, noFallbackConfig);

        assertThatThrownBy(() -> noFallbackRoutingManager.provideBackendConfiguration("no_backends_configured_for_this_group", ""))
                .isInstanceOf(NoHealthyBackendException.class);
    }

    @Test
    void testUnavailableGroupFallbackDisabledStillServesHealthyGroup()
    {
        String groupName = "fallback_disabled_but_healthy_group";
        ProxyBackendConfiguration backend = new ProxyBackendConfiguration();
        backend.setActive(true);
        backend.setRoutingGroup(groupName);
        backend.setName(groupName + "-backend");
        backend.setProxyTo(groupName + ".trino.example.com");
        backend.setExternalUrl("trino.example.com");
        backendManager.addBackend(backend);

        RoutingConfiguration noFallbackConfig = new RoutingConfiguration();
        noFallbackConfig.setUnavailableGroupFallbackEnabled(false);
        RoutingManager noFallbackRoutingManager = new StochasticRoutingManager(backendManager, historyManager, noFallbackConfig);
        // Health is tracked per RoutingManager instance, so it must be set on the instance under test.
        noFallbackRoutingManager.updateBackEndHealth(backend.getName(), TrinoStatus.HEALTHY);

        assertThat(noFallbackRoutingManager.provideBackendConfiguration(groupName, "").getProxyTo())
                .isEqualTo(groupName + ".trino.example.com");
    }

    @Test
    void testUnavailableGroupFallbackDisabledWithUnhealthyDefaultGroup()
    {
        RoutingConfiguration noFallbackConfig = new RoutingConfiguration();
        noFallbackConfig.setUnavailableGroupFallbackEnabled(false);
        ProxyBackendConfiguration backend = new ProxyBackendConfiguration();
        backend.setActive(true);
        backend.setRoutingGroup(noFallbackConfig.getDefaultRoutingGroup());
        backend.setName("default-group-unhealthy-backend");
        backend.setProxyTo("default-group-unhealthy-backend.trino.example.com");
        backend.setExternalUrl("trino.example.com");
        backendManager.addBackend(backend);
        RoutingManager noFallbackRoutingManager = new StochasticRoutingManager(backendManager, historyManager, noFallbackConfig);
        noFallbackRoutingManager.updateBackEndHealth(backend.getName(), TrinoStatus.UNHEALTHY);

        assertThatThrownBy(() -> noFallbackRoutingManager.provideBackendConfiguration(noFallbackConfig.getDefaultRoutingGroup(), ""))
                .isInstanceOf(NoHealthyBackendException.class)
                .hasMessage("No healthy backend found for default routing group [adhoc]");
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
}
