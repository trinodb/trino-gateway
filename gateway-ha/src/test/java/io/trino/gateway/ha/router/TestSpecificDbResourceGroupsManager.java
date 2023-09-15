package io.trino.gateway.ha.router;

import static io.trino.gateway.ha.router.ResourceGroupsManager.ResourceGroupsDetail;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.trino.gateway.ha.HaGatewayTestUtils;
import io.trino.gateway.ha.config.DataStoreConfiguration;
import io.trino.gateway.ha.persistence.JdbcConnectionManager;
import java.io.File;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

@Slf4j
@TestInstance(Lifecycle.PER_CLASS)
public class TestSpecificDbResourceGroupsManager extends TestResourceGroupsManager {
  private String specificDb;

  @BeforeAll
  @Override
  public void setUp() {
    specificDb = "h2db-" + System.currentTimeMillis();
    File baseDir = new File(System.getProperty("java.io.tmpdir"));
    File tempH2DbDir = new File(baseDir, specificDb);
    tempH2DbDir.deleteOnExit();
    String jdbcUrl = "jdbc:h2:" + tempH2DbDir.getAbsolutePath();
    HaGatewayTestUtils.seedRequiredData(
            new HaGatewayTestUtils.TestConfig("", tempH2DbDir.getAbsolutePath()));
    DataStoreConfiguration db = new DataStoreConfiguration(jdbcUrl, "sa",
            "sa", "org.h2.Driver", 4, 4);
    JdbcConnectionManager connectionManager = new JdbcConnectionManager(db);
    super.resourceGroupManager = new HaResourceGroupsManager(connectionManager);
  }

  private void createResourceGroup() {
    ResourceGroupsDetail resourceGroup = new ResourceGroupsDetail();

    resourceGroup.setResourceGroupId(1L);
    resourceGroup.setName("admin2");
    resourceGroup.setHardConcurrencyLimit(20);
    resourceGroup.setMaxQueued(200);
    resourceGroup.setJmxExport(true);
    resourceGroup.setSoftMemoryLimit("80%");

    ResourceGroupsDetail newResourceGroup = resourceGroupManager.createResourceGroup(resourceGroup,
            specificDb);
  }

  @Test
  public void testReadSpecificDbResourceGroupCauseException() {
    assertThrows(Exception.class, () -> {
      List<ResourceGroupsDetail> resourceGroups = resourceGroupManager.readAllResourceGroups("abcd");
    });
  }

  @Test
  public void testReadSpecificDbResourceGroup() {
    this.createResourceGroup();
    List<ResourceGroupsDetail> resourceGroups = resourceGroupManager
            .readAllResourceGroups(specificDb);
    assertNotNull(resourceGroups);
    resourceGroupManager.deleteResourceGroup(1,specificDb);
  }

  @Test
  public void testReadSpecificDbSelector() {
    this.createResourceGroup();
    ResourceGroupsManager.SelectorsDetail selector = new ResourceGroupsManager.SelectorsDetail();
    selector.setResourceGroupId(1L);
    selector.setPriority(0L);
    selector.setUserRegex("data-platform-admin");
    selector.setSourceRegex("admin2");
    selector.setQueryType("query_type");
    selector.setClientTags("client_tag");
    selector.setSelectorResourceEstimate("estimate");

    ResourceGroupsManager.SelectorsDetail newSelector = resourceGroupManager
            .createSelector(selector, specificDb);

    assertEquals(selector, newSelector);
    resourceGroupManager
            .deleteSelector(selector, specificDb);
    resourceGroupManager.deleteResourceGroup(1,specificDb);
  }
}
