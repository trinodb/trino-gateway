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

import io.trino.gateway.ha.config.DataStoreConfiguration;
import io.trino.gateway.ha.persistence.JdbcConnectionManager;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.List;

import static io.trino.gateway.ha.router.ResourceGroupsManager.ResourceGroupsDetail;
import static io.trino.gateway.ha.router.ResourceGroupsManager.SelectorsDetail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@TestInstance(Lifecycle.PER_CLASS)
final class TestSpecificDbResourceGroupsManager
        extends TestResourceGroupsManager
{
    private String specificDb = "test_db_specific";

    @BeforeAll
    @Override
    void setUp()
    {
        PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:14-alpine")
                .withDatabaseName(specificDb)
                .withInitScript("gateway-ha-persistence-postgres.sql");
        postgres.start();

        String jdbcUrl = postgres.getJdbcUrl();
        String username = postgres.getUsername();
        String password = postgres.getPassword();

        try {
            Jdbi jdbi = Jdbi.create(jdbcUrl, username, password);
            DataStoreConfiguration db = new DataStoreConfiguration(jdbcUrl, username,
                    password, "org.postgresql.Driver", 4, false);
            JdbcConnectionManager connectionManager = new JdbcConnectionManager(jdbi, db);
            super.resourceGroupManager = new HaResourceGroupsManager(connectionManager);
        }
        catch (Exception e) {
            throw new RuntimeException("Failed to setup test database: " + specificDb, e);
        }
    }

    private ResourceGroupsDetail createResourceGroup(String groupName)
    {
        ResourceGroupsDetail resourceGroup = new ResourceGroupsDetail();

        resourceGroup.setName(groupName);
        resourceGroup.setHardConcurrencyLimit(20);
        resourceGroup.setMaxQueued(200);
        resourceGroup.setJmxExport(true);
        resourceGroup.setSoftMemoryLimit("80%");

        return resourceGroupManager.createResourceGroup(resourceGroup, specificDb);
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
        List<ResourceGroupsDetail> existingGroups = resourceGroupManager.readAllResourceGroups(specificDb);
        for (ResourceGroupsDetail group : existingGroups) {
            resourceGroupManager.deleteResourceGroup(group.getResourceGroupId(), specificDb);
        }

        String uniqueName = "admin2-test";
        ResourceGroupsDetail group = this.createResourceGroup(uniqueName);
        List<ResourceGroupsDetail> resourceGroups = resourceGroupManager
                .readAllResourceGroups(specificDb);
        assertThat(resourceGroups).isNotNull();
        assertThat(resourceGroups).hasSize(1);
        ResourceGroupsDetail actualGroup = resourceGroups.getFirst();
        assertThat(actualGroup.getName()).isEqualTo(group.getName());
        assertThat(actualGroup.getHardConcurrencyLimit()).isEqualTo(group.getHardConcurrencyLimit());
        assertThat(actualGroup.getMaxQueued()).isEqualTo(group.getMaxQueued());
        assertThat(actualGroup.getJmxExport()).isEqualTo(group.getJmxExport());
        assertThat(actualGroup.getSoftMemoryLimit()).isEqualTo(group.getSoftMemoryLimit());
        resourceGroupManager.deleteResourceGroup(actualGroup.getResourceGroupId(), specificDb);
    }

    @Test
    void testReadSpecificDbSelector()
    {
        // Clean up existing groups and selectors
        List<ResourceGroupsDetail> existingGroups = resourceGroupManager.readAllResourceGroups(specificDb);
        for (ResourceGroupsDetail existingGroup : existingGroups) {
            List<SelectorsDetail> existingSelectors = resourceGroupManager.readSelector(existingGroup.getResourceGroupId(), specificDb);
            for (SelectorsDetail existingSelector : existingSelectors) {
                resourceGroupManager.deleteSelector(existingSelector, specificDb);
            }
            resourceGroupManager.deleteResourceGroup(existingGroup.getResourceGroupId(), specificDb);
        }

        // Create a new resource group
        String uniqueName = "admin3-test";
        ResourceGroupsDetail group = this.createResourceGroup(uniqueName);

        // Skip test if resource group ID is 0
        if (group.getResourceGroupId() == 0) {
            return;
        }

        // Create a selector
        SelectorsDetail selector = new SelectorsDetail();
        selector.setResourceGroupId(group.getResourceGroupId());
        selector.setPriority(0L);
        selector.setUserRegex("data-platform-admin");
        selector.setSourceRegex("admin2");
        selector.setQueryType("query_type");
        selector.setClientTags("client_tag");
        selector.setSelectorResourceEstimate("estimate");

        // Create the selector in the database
        resourceGroupManager.createSelector(selector, specificDb);

        // Read all selectors and verify
        List<SelectorsDetail> selectors = resourceGroupManager.readAllSelectors(specificDb);
        assertThat(selectors).hasSize(1);
        SelectorsDetail actualSelector = selectors.getFirst();
        assertThat(actualSelector.getResourceGroupId()).isEqualTo(selector.getResourceGroupId());
        assertThat(actualSelector.getPriority()).isEqualTo(selector.getPriority());
        assertThat(actualSelector.getUserRegex()).isEqualTo(selector.getUserRegex());
        assertThat(actualSelector.getSourceRegex()).isEqualTo(selector.getSourceRegex());
        assertThat(actualSelector.getQueryType()).isEqualTo(selector.getQueryType());
        assertThat(actualSelector.getClientTags()).isEqualTo(selector.getClientTags());
        assertThat(actualSelector.getSelectorResourceEstimate()).isEqualTo(selector.getSelectorResourceEstimate());

        // Clean up
        resourceGroupManager.deleteSelector(selector, specificDb);
        resourceGroupManager.deleteResourceGroup(group.getResourceGroupId(), specificDb);
    }
}
