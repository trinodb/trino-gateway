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

import io.trino.gateway.ha.HaGatewayTestUtils;
import io.trino.gateway.ha.config.DataStoreConfiguration;
import io.trino.gateway.ha.config.ProxyBackendConfiguration;
import io.trino.gateway.ha.persistence.JdbcConnectionManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(Lifecycle.PER_CLASS)
public class TestHaRoutingManager
{
    RoutingManager haRoutingManager;
    GatewayBackendManager backendManager;
    QueryHistoryManager historyManager;

    @BeforeAll
    public void setUp()
    {
        File baseDir = new File(System.getProperty("java.io.tmpdir"));
        File tempH2DbDir = new File(baseDir, "h2db-" + System.currentTimeMillis());
        tempH2DbDir.deleteOnExit();
        String jdbcUrl = "jdbc:h2:" + tempH2DbDir.getAbsolutePath();
        HaGatewayTestUtils.seedRequiredData(
                new HaGatewayTestUtils.TestConfig("", tempH2DbDir.getAbsolutePath()));
        DataStoreConfiguration db = new DataStoreConfiguration(jdbcUrl, "sa", "sa", "org.h2.Driver", 4);
        JdbcConnectionManager connectionManager = new JdbcConnectionManager(db);
        backendManager = new HaGatewayManager(connectionManager);
        historyManager = new HaQueryHistoryManager(connectionManager);
        haRoutingManager = new HaRoutingManager(backendManager, historyManager);
    }

    @Test
    private void addMockBackends()
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
            //set backend as healthy start with
            haRoutingManager.upateBackEndHealth(backend, true);
        }

        //Keep only 1st backend as healthy, mark all the others as unhealthy
        assertTrue(!backendManager.getAllActiveBackends().isEmpty());

        for (int i = 1; i < numBackends; i++) {
            backend = groupName + i;
            haRoutingManager.upateBackEndHealth(backend, false);
        }

        assertEquals(haRoutingManager.provideBackendForRoutingGroup(groupName, ""),
                "test_group0.trino.example.com");
    }
}
