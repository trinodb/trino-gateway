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
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@TestInstance(Lifecycle.PER_CLASS)
final class TestSpecificDbResourceGroupsManager
        extends TestResourceGroupsManager
{
    private String specificDb;

    private PostgreSQLContainer<?> postgres;

    @BeforeAll
    @Override
    void setUp()
    {
        specificDb = "pg_test_db_" + System.currentTimeMillis();
        postgres = new PostgreSQLContainer<>("postgres:14-alpine")
                .withDatabaseName(specificDb)
                .withUsername("test")
                .withPassword("test");
        postgres.start();

        String jdbcUrl = postgres.getJdbcUrl();
        DataStoreConfiguration db = new DataStoreConfiguration(
                jdbcUrl,
                postgres.getUsername(),
                postgres.getPassword(),
                "org.postgresql.Driver",
                4,
                true);

        Jdbi jdbi = Jdbi.create(jdbcUrl, postgres.getUsername(), postgres.getPassword());
        HaGatewayTestUtils.seedRequiredDataPostgres(jdbi);
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
        // Create the resource group with a unique name
        String uniqueName = "admin2_" + System.currentTimeMillis();

        try {
            // Create the resource group
            ResourceGroupsDetail resourceGroup = new ResourceGroupsDetail();
            resourceGroup.setName(uniqueName);
            resourceGroup.setHardConcurrencyLimit(20);
            resourceGroup.setMaxQueued(200);
            resourceGroup.setJmxExport(true);
            resourceGroup.setSoftMemoryLimit("80%");
            ResourceGroupsDetail createdGroup = resourceGroupManager.createResourceGroup(resourceGroup, specificDb);

            // Get the assigned ID
            Long resourceGroupId = createdGroup.getResourceGroupId();
            assertThat(resourceGroupId).isNotNull();

            // Read all resource groups
            List<ResourceGroupsDetail> resourceGroups = resourceGroupManager
                    .readAllResourceGroups(specificDb);

            // Basic assertions
            assertThat(resourceGroups).isNotNull();
            assertThat(resourceGroups).isNotEmpty();

            // Print all resource groups for debugging
            System.out.println("Found " + resourceGroups.size() + " resource groups:");
            for (ResourceGroupsDetail group : resourceGroups) {
                System.out.println("Group: id=" + group.getResourceGroupId() + ", name=" + group.getName());
            }

            // Verify the resource group was created with the expected properties
            boolean foundGroup = false;
            for (ResourceGroupsDetail group : resourceGroups) {
                if (uniqueName.equals(group.getName())) {
                    foundGroup = true;
                    assertThat(group.getHardConcurrencyLimit()).isEqualTo(20);
                    assertThat(group.getMaxQueued()).isEqualTo(200);
                    assertThat(group.getJmxExport()).isEqualTo(Boolean.TRUE);
                    assertThat(group.getSoftMemoryLimit()).isEqualTo("80%");
                    break;
                }
            }
            assertThat(foundGroup).isTrue();
        }
        finally {
            // Clean up - we don't need to specify an ID since we're using a unique name
            List<ResourceGroupsDetail> resourceGroups = resourceGroupManager.readAllResourceGroups(specificDb);
            for (ResourceGroupsDetail group : resourceGroups) {
                if (uniqueName.equals(group.getName())) {
                    resourceGroupManager.deleteResourceGroup(group.getResourceGroupId(), specificDb);
                    break;
                }
            }
        }
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
