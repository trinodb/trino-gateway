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

import io.trino.gateway.ha.config.ProxyBackendConfiguration;
import io.trino.gateway.ha.persistence.JdbcConnectionManager;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import static io.trino.gateway.ha.TestingJdbcConnectionManager.createTestingJdbcConnectionManager;
import static io.trino.gateway.ha.handler.QueryIdCachingProxyHandler.USER_HEADER;
import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(Lifecycle.PER_CLASS)
public class TestStochasticRoutingManager
{
    static final String ADHOC = "adhoc";
    static int numTestBackends;
    static String groupName;

    RoutingManager haRoutingManager;
    GatewayBackendManager backendManager;
    QueryHistoryManager historyManager;
    MultivaluedMap<String, String> headers;

    @BeforeAll
    public void setUp()
    {
        JdbcConnectionManager connectionManager = createTestingJdbcConnectionManager();
        backendManager = new HaGatewayManager(connectionManager.getJdbi());
        historyManager = new HaQueryHistoryManager(connectionManager.getJdbi());
        haRoutingManager = new StochasticRoutingManager(backendManager, historyManager);
        headers = new MultivaluedHashMap<>();

        // test group
        groupName = "test_group";
        numTestBackends = 5;
        String backend;
        // add mock backends
        for (int i = 0; i < numTestBackends; i++) {
            backend = groupName + i;
            ProxyBackendConfiguration proxyBackend = new ProxyBackendConfiguration();
            proxyBackend.setActive(true);
            proxyBackend.setRoutingGroup(groupName);
            proxyBackend.setName(backend);
            proxyBackend.setProxyTo(backend + ".trino.example.com");
            proxyBackend.setExternalUrl("trino.example.com");
            backendManager.addBackend(proxyBackend);
            //set backend as healthy start with
            haRoutingManager.updateBackEndHealth(backend, true);
        }

        // adhoc group
        // add mock backend
        ProxyBackendConfiguration proxyBackend = new ProxyBackendConfiguration();
        proxyBackend.setActive(true);
        proxyBackend.setRoutingGroup(ADHOC);
        proxyBackend.setName(ADHOC);
        proxyBackend.setProxyTo(ADHOC + ".trino.example.com");
        proxyBackend.setExternalUrl("trino.example.com");
        backendManager.addBackend(proxyBackend);
        //set backend as healthy start with
        haRoutingManager.updateBackEndHealth(ADHOC, true);
    }

    @BeforeEach
    public void init()
    {
        headers.clear();
        // before each class, reset health status for all as true
        haRoutingManager.updateBackEndHealth(ADHOC, true);
        for (int i = 0; i < numTestBackends; i++) {
            String backend = groupName + i;
            haRoutingManager.updateBackEndHealth(backend, true);
        }
    }

    @Test
    public void testActiveBackendCount()
    {
        assertThat((long) backendManager.getAllActiveBackends().size()).isEqualTo(numTestBackends + 1);
    }

    @Test
    public void testNoHealthyTestBackend()
    {
        // set test backends to false
        for (int i = 0; i < numTestBackends; i++) {
            String backend = groupName + i;
            haRoutingManager.updateBackEndHealth(backend, false);
        }
        headers.add(USER_HEADER, "u1");

        assertThat(haRoutingManager.provideBackendForRoutingGroup(groupName, headers))
                .isEqualTo("adhoc.trino.example.com");
    }

    @Test
    public void testKeepOnlyFirstTestBackendHealthy()
    {
        //Keep only 1st backend as healthy, mark all the others as unhealthy
        for (int i = 1; i < numTestBackends; i++) {
            String backend = groupName + i;
            haRoutingManager.updateBackEndHealth(backend, false);
        }
        haRoutingManager.updateBackEndHealth(ADHOC, false);
        headers.add(USER_HEADER, "u2");

        assertThat(haRoutingManager.provideBackendForRoutingGroup(groupName, headers))
                .isEqualTo("test_group0.trino.example.com");
    }

    @Test
    public void testNoUserHeader()
    {
        // set default backends to false
        for (int i = 0; i < numTestBackends; i++) {
            String backend = groupName + i;
            haRoutingManager.updateBackEndHealth(backend, false);
        }
        // adding user header is skipped on purpose

        assertThat(haRoutingManager.provideBackendForRoutingGroup(groupName, headers))
                .isEqualTo(ADHOC + ".trino.example.com");
    }
}
