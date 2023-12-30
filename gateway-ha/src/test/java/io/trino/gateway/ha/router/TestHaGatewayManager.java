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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(Lifecycle.PER_CLASS)
public class TestHaGatewayManager
{
    private HaGatewayManager haGatewayManager;

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
        haGatewayManager = new HaGatewayManager(connectionManager);
    }

    @Test
    @Order(1)
    public void testAddBackend()
    {
        ProxyBackendConfiguration backend = new ProxyBackendConfiguration();
        backend.setActive(true);
        backend.setRoutingGroup("adhoc");
        backend.setName("adhoc1");
        backend.setProxyTo("adhoc1.trino.gateway.io");
        backend.setExternalUrl("adhoc1.trino.gateway.io");
        ProxyBackendConfiguration updated = haGatewayManager.addBackend(backend);
        assertEquals(backend, updated);
    }

    @Test
    @Order(2)
    public void testGetBackends()
    {
        List<ProxyBackendConfiguration> backends = haGatewayManager.getAllBackends();
        assertEquals(1, backends.size());

        backends = haGatewayManager.getActiveBackends("adhoc");
        assertEquals(1, backends.size());

        backends = haGatewayManager.getActiveBackends("unknown");
        assertEquals(0, backends.size());

        backends = haGatewayManager.getActiveAdhocBackends();
        assertEquals(1, backends.size());
    }

    @Test
    @Order(3)
    public void testUpdateBackend()
    {
        ProxyBackendConfiguration backend = new ProxyBackendConfiguration();
        backend.setActive(false);
        backend.setRoutingGroup("adhoc");
        backend.setName("new-adhoc1");
        backend.setProxyTo("adhoc1.trino.gateway.io");
        backend.setExternalUrl("adhoc1.trino.gateway.io");
        haGatewayManager.updateBackend(backend);
        List<ProxyBackendConfiguration> backends = haGatewayManager.getActiveBackends("adhoc");
        assertEquals(1, backends.size());

        backend.setActive(false);
        backend.setRoutingGroup("etl");
        backend.setName("adhoc1");
        backend.setProxyTo("adhoc2.trino.gateway.io");
        backend.setExternalUrl("adhoc2.trino.gateway.io");
        haGatewayManager.updateBackend(backend);
        backends = haGatewayManager.getActiveBackends("adhoc");
        assertEquals(0, backends.size());
        backends = haGatewayManager.getAllBackends();
        assertEquals(2, backends.size());
        assertEquals("etl", backends.get(1).getRoutingGroup());
    }

    @Test
    @Order(4)
    public void testDeleteBackend()
    {
        List<ProxyBackendConfiguration> backends = haGatewayManager.getAllBackends();
        assertEquals(2, backends.size());
        assertEquals("etl", backends.get(1).getRoutingGroup());
        haGatewayManager.deleteBackend(backends.get(0).getName());
        backends = haGatewayManager.getAllBackends();
        assertEquals(1, backends.size());
    }

    @AfterAll
    public void cleanUp()
    {
    }
}
