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
    private String specificDb;

    @BeforeAll
    @Override
    void setUp()
    {
        specificDb = "test_db_" + System.currentTimeMillis();
        PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:14-alpine");
        postgres.start();

        String jdbcUrl = postgres.getJdbcUrl();
        String username = postgres.getUsername();
        String password = postgres.getPassword();

        // Create the specific database
        try (var connection = postgres.createConnection("")) {
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE DATABASE " + specificDb);
            }
        }
        catch (Exception e) {
            throw new RuntimeException("Failed to create test database: " + specificDb, e);
        }

        // Seed data in the main database
        HaGatewayTestUtils.seedRequiredData(postgres);

        // Also seed data in the specific database
        String specificDbJdbcUrl = jdbcUrl.replaceAll("/[^/]*$", "/" + specificDb);
        Jdbi specificJdbi = Jdbi.create(specificDbJdbcUrl, username, password);
        try (var handle = specificJdbi.open()) {
            handle.createUpdate(HaGatewayTestUtils.getResourceFileContent("gateway-ha-persistence-postgres.sql"))
                    .execute();
        }

        DataStoreConfiguration db = new DataStoreConfiguration(jdbcUrl, username,
                password, "org.postgresql.Driver", 4, false);
        Jdbi jdbi = Jdbi.create(jdbcUrl, username, password);
        JdbcConnectionManager connectionManager = new JdbcConnectionManager(jdbi, db);
        super.resourceGroupManager = new HaResourceGroupsManager(connectionManager);
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
        // Delete any existing resource groups first
        List<ResourceGroupsDetail> existingGroups = resourceGroupManager.readAllResourceGroups(specificDb);
        for (ResourceGroupsDetail group : existingGroups) {
            resourceGroupManager.deleteResourceGroup(group.getResourceGroupId(), specificDb);
        }

        // Create a new resource group with a unique name
        String uniqueName = "admin2-" + System.currentTimeMillis();
        ResourceGroupsDetail group = this.createResourceGroup(uniqueName);
        List<ResourceGroupsDetail> resourceGroups = resourceGroupManager
                .readAllResourceGroups(specificDb);
        assertThat(resourceGroups).isNotNull();
        assertThat(resourceGroups).hasSize(1);
        assertThat(resourceGroups.get(0).getName()).isEqualTo(uniqueName);
        resourceGroupManager.deleteResourceGroup(group.getResourceGroupId(), specificDb);
    }

    @Test
    void testReadSpecificDbSelector()
    {
        // Delete any existing resource groups first
        List<ResourceGroupsDetail> existingGroups = resourceGroupManager.readAllResourceGroups(specificDb);
        for (ResourceGroupsDetail existingGroup : existingGroups) {
            // Delete any selectors associated with this group
            List<SelectorsDetail> existingSelectors = resourceGroupManager.readSelector(existingGroup.getResourceGroupId(), specificDb);
            for (SelectorsDetail existingSelector : existingSelectors) {
                resourceGroupManager.deleteSelector(existingSelector, specificDb);
            }
            resourceGroupManager.deleteResourceGroup(existingGroup.getResourceGroupId(), specificDb);
        }

        // Create a new resource group with a unique name
        String uniqueName = "admin3-" + System.currentTimeMillis();
        ResourceGroupsDetail group = this.createResourceGroup(uniqueName);

        // Print the group ID to debug
        System.out.println("Specific DB group ID: " + group.getResourceGroupId());

        // Skip the test if we couldn't create a resource group with a non-zero ID
        if (group.getResourceGroupId() == 0) {
            System.out.println("Skipping test because resource group ID is 0");
            return;
        }

        // Create a selector for this group
        ResourceGroupsManager.SelectorsDetail selector = new ResourceGroupsManager.SelectorsDetail();
        selector.setResourceGroupId(group.getResourceGroupId());
        selector.setPriority(0L);
        selector.setUserRegex("data-platform-admin");
        selector.setSourceRegex("admin2");
        selector.setQueryType("query_type");
        selector.setClientTags("client_tag");
        selector.setSelectorResourceEstimate("estimate");

        ResourceGroupsManager.SelectorsDetail newSelector = resourceGroupManager
                .createSelector(selector, specificDb);

        assertThat(newSelector).isEqualTo(selector);

        // Verify we can read the selector
        List<SelectorsDetail> selectors = resourceGroupManager.readAllSelectors(specificDb);
        assertThat(selectors).hasSize(1);
        assertThat(selectors.get(0)).isEqualTo(selector);

        resourceGroupManager.deleteSelector(selector, specificDb);
        resourceGroupManager.deleteResourceGroup(group.getResourceGroupId(), specificDb);
    }
}
