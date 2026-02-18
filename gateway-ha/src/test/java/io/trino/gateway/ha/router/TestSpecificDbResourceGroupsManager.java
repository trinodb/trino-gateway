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
import io.trino.gateway.ha.module.HaGatewayProviderModule;
import io.trino.gateway.ha.persistence.JdbcConnectionManager;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import static io.trino.gateway.ha.router.ResourceGroupsManager.ResourceGroupsDetail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@TestInstance(Lifecycle.PER_CLASS)
final class TestSpecificDbResourceGroupsManager
        extends TestResourceGroupsManager
{
    private String specificDb;

    @BeforeAll
    @Override
    void setUp()
    {
        specificDb = "h2db-" + System.currentTimeMillis();
        File tempH2DbDir = Path.of(System.getProperty("java.io.tmpdir"), specificDb).toFile();
        tempH2DbDir.deleteOnExit();
        String jdbcUrl = "jdbc:h2:" + tempH2DbDir.getAbsolutePath() + ";NON_KEYWORDS=NAME,VALUE";
        HaGatewayTestUtils.seedRequiredData(tempH2DbDir.getAbsolutePath());
        DataStoreConfiguration db = new DataStoreConfiguration(jdbcUrl, "sa",
                "sa", "org.h2.Driver", true, 4, false);
        Jdbi jdbi = HaGatewayProviderModule.createJdbi(db);
        JdbcConnectionManager connectionManager = new JdbcConnectionManager(jdbi, db);
        super.resourceGroupManager = new HaResourceGroupsManager(connectionManager);
    }

    private void createResourceGroup(String groupName)
    {
        ResourceGroupsDetail resourceGroup = new ResourceGroupsDetail();

        resourceGroup.setResourceGroupId(1L);
        resourceGroup.setName(groupName);
        resourceGroup.setHardConcurrencyLimit(20);
        resourceGroup.setMaxQueued(200);
        resourceGroup.setJmxExport(true);
        resourceGroup.setSoftMemoryLimit("80%");

        resourceGroupManager.createResourceGroup(resourceGroup, specificDb);
    }

    @Test
    void testReadSpecificDbResourceGroupCauseException()
    {
        assertThatThrownBy(() -> resourceGroupManager.readAllResourceGroups("abcd"))
                .isInstanceOf(Exception.class);
    }

    @Test
    void testReadSpecificDbResourceGroup()
    {
        this.createResourceGroup("admin2");
        List<ResourceGroupsDetail> resourceGroups = resourceGroupManager
                .readAllResourceGroups(specificDb);
        assertThat(resourceGroups).isNotNull();
        resourceGroupManager.deleteResourceGroup(1, specificDb);
    }

    @Test
    void testReadSpecificDbSelector()
    {
        this.createResourceGroup("admin3");
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

        assertThat(newSelector).isEqualTo(selector);
        resourceGroupManager
                .deleteSelector(selector, specificDb);
        resourceGroupManager.deleteResourceGroup(1, specificDb);
    }
}
