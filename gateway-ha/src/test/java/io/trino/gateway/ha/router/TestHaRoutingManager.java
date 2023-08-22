package io.trino.gateway.ha.router;

import io.trino.gateway.ha.HaGatewayTestUtils;
import io.trino.gateway.ha.config.DataStoreConfiguration;
import io.trino.gateway.ha.config.ProxyBackendConfiguration;
import io.trino.gateway.ha.persistence.JdbcConnectionManager;
import java.io.File;

import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

@Slf4j
@Test
public class TestHaRoutingManager {
  RoutingManager haRoutingManager;
  GatewayBackendManager backendManager;
  QueryHistoryManager historyManager;

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
    backendManager = new HaGatewayManager(connectionManager);
    historyManager = new HaQueryHistoryManager(connectionManager);
    haRoutingManager = new HaRoutingManager(backendManager, historyManager);

  }

  @Test
  private void addMockBackends() {
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
      //set backend as healthyti start with
      haRoutingManager.upateBackEndHealth(backend, true);
    }

    //Keep only 1st backend as healthy, mark all the others as unhealthy
    assert (!backendManager.getAllActiveBackends().isEmpty());

    for (int i = 1; i < numBackends; i++) {
      backend = groupName + i;
      haRoutingManager.upateBackEndHealth(backend, false);
    }

    assert (haRoutingManager.provideBackendForRoutingGroup(groupName, "")
            .equals("test_group0.trino.example.com"));
  }

}


