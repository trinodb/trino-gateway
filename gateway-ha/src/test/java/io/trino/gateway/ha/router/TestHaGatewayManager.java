package io.trino.gateway.ha.router;

import io.trino.gateway.ha.HaGatewayTestUtils;
import io.trino.gateway.ha.config.DataStoreConfiguration;
import io.trino.gateway.ha.config.ProxyBackendConfiguration;
import io.trino.gateway.ha.persistence.JdbcConnectionManager;
import java.io.File;
import java.util.List;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

@Test
public class TestHaGatewayManager {
  private HaGatewayManager haGatewayManager;

  @BeforeClass(alwaysRun = true)
  public void setUp() {
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

  public void testAddBackend() {
    ProxyBackendConfiguration backend = new ProxyBackendConfiguration();
    backend.setActive(true);
    backend.setRoutingGroup("adhoc");
    backend.setName("adhoc1");
    backend.setProxyTo("adhoc1.trino.gateway.io");
    backend.setExternalUrl("adhoc1.trino.gateway.io");
    ProxyBackendConfiguration updated = haGatewayManager.addBackend(backend);
    Assert.assertEquals(updated, backend);
  }

  @Test(dependsOnMethods = {"testAddBackend"})
  public void testGetBackends() {
    List<ProxyBackendConfiguration> backends = haGatewayManager.getAllBackends();
    Assert.assertEquals(backends.size(), 1);

    backends = haGatewayManager.getActiveBackends("adhoc");
    Assert.assertEquals(backends.size(), 1);

    backends = haGatewayManager.getActiveBackends("unknown");
    Assert.assertEquals(backends.size(), 0);

    backends = haGatewayManager.getActiveAdhocBackends();
    Assert.assertEquals(backends.size(), 1);
  }

  @Test(dependsOnMethods = {"testGetBackends"})
  public void testUpdateBackend() {
    ProxyBackendConfiguration backend = new ProxyBackendConfiguration();
    backend.setActive(false);
    backend.setRoutingGroup("adhoc");
    backend.setName("new-adhoc1");
    backend.setProxyTo("adhoc1.trino.gateway.io");
    backend.setExternalUrl("adhoc1.trino.gateway.io");
    haGatewayManager.updateBackend(backend);
    List<ProxyBackendConfiguration> backends = haGatewayManager.getActiveBackends("adhoc");
    Assert.assertEquals(backends.size(), 1);

    backend.setActive(false);
    backend.setRoutingGroup("etl");
    backend.setName("adhoc1");
    backend.setProxyTo("adhoc2.trino.gateway.io");
    backend.setExternalUrl("adhoc2.trino.gateway.io");
    haGatewayManager.updateBackend(backend);
    backends = haGatewayManager.getActiveBackends("adhoc");
    Assert.assertEquals(backends.size(), 0);
    backends = haGatewayManager.getAllBackends();
    Assert.assertEquals(backends.size(), 2);
    Assert.assertEquals(backends.get(1).getRoutingGroup(), "etl");
  }

  @Test(dependsOnMethods = {"testUpdateBackend"})
  public void testDeleteBackend() {
    List<ProxyBackendConfiguration> backends = haGatewayManager.getAllBackends();
    Assert.assertEquals(backends.size(), 2);
    Assert.assertEquals(backends.get(1).getRoutingGroup(), "etl");
    haGatewayManager.deleteBackend(backends.get(0).getName());
    backends = haGatewayManager.getAllBackends();
    Assert.assertEquals(backends.size(), 1);
  }

  @AfterClass(alwaysRun = true)
  public void cleanUp() {
  }
}
