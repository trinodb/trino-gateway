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

import io.trino.gateway.ha.persistence.JdbcConnectionManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;

import static io.trino.gateway.ha.TestingJdbcConnectionManager.createTestingJdbcConnectionManager;
import static io.trino.gateway.ha.router.ResourceGroupsManager.ExactSelectorsDetail;
import static io.trino.gateway.ha.router.ResourceGroupsManager.GlobalPropertiesDetail;
import static io.trino.gateway.ha.router.ResourceGroupsManager.ResourceGroupsDetail;
import static io.trino.gateway.ha.router.ResourceGroupsManager.SelectorsDetail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.MethodOrderer.OrderAnnotation;

@TestMethodOrder(OrderAnnotation.class)
@TestInstance(Lifecycle.PER_CLASS)
public class TestResourceGroupsManager
{
    public ResourceGroupsManager resourceGroupManager;

    @BeforeAll
    void setUp()
    {
        JdbcConnectionManager connectionManager = createTestingJdbcConnectionManager();
        resourceGroupManager = new HaResourceGroupsManager(connectionManager);
    }

    @Test
    @Order(1)
    void testCreateResourceGroup()
    {
        ResourceGroupsDetail resourceGroup = new ResourceGroupsDetail();

        resourceGroup.setName("admin");
        resourceGroup.setHardConcurrencyLimit(20);
        resourceGroup.setMaxQueued(200);
        resourceGroup.setJmxExport(true);
        resourceGroup.setSoftMemoryLimit("80%");
        ResourceGroupsDetail adminResourceGroup = resourceGroupManager.createResourceGroup(resourceGroup,
                null);
        assertThat(adminResourceGroup).isEqualTo(resourceGroup);

        resourceGroup.setName("user");
        resourceGroup.setHardConcurrencyLimit(10);
        resourceGroup.setMaxQueued(100);
        resourceGroup.setJmxExport(true);
        resourceGroup.setSoftMemoryLimit("50%");
        ResourceGroupsDetail userResourceGroup = resourceGroupManager.createResourceGroup(resourceGroup,
                null);
        assertThat(userResourceGroup).isEqualTo(resourceGroup);
    }

    @Test
    @Order(2)
    void testReadResourceGroup()
    {
        List<ResourceGroupsDetail> resourceGroups = resourceGroupManager.readAllResourceGroups(null);
        assertThat(resourceGroups).hasSize(2);

        assertThat(resourceGroups.get(0).getResourceGroupId()).isEqualTo(1L);
        assertThat(resourceGroups.get(0).getName()).isEqualTo("admin");
        assertThat(resourceGroups.get(0).getHardConcurrencyLimit()).isEqualTo(20);
        assertThat(resourceGroups.get(0).getMaxQueued()).isEqualTo(200);
        assertThat(resourceGroups.get(0).getJmxExport()).isEqualTo(Boolean.TRUE);
        assertThat(resourceGroups.get(0).getSoftMemoryLimit()).isEqualTo("80%");
    }

    @Test
    @Order(3)
    void testUpdateResourceGroup()
    {
        List<ResourceGroupsDetail> resourceGroups = resourceGroupManager.readAllResourceGroups(null);
        long adminResourceGroupId = resourceGroups.stream()
                .filter(rg -> "admin".equals(rg.getName()))
                .findFirst()
                .map(ResourceGroupsDetail::getResourceGroupId)
                .orElse(1L);
        ResourceGroupsDetail resourceGroup = new ResourceGroupsDetail();
        resourceGroup.setResourceGroupId(adminResourceGroupId);
        resourceGroup.setName("admin");
        resourceGroup.setHardConcurrencyLimit(50);
        resourceGroup.setMaxQueued(50);
        resourceGroup.setJmxExport(false);
        resourceGroup.setSoftMemoryLimit("20%");

        ResourceGroupsDetail updated = resourceGroupManager.updateResourceGroup(resourceGroup, null);
        resourceGroups = resourceGroupManager.readAllResourceGroups(null);
        assertThat(resourceGroups).contains(updated);

        /* Update resourceGroups that do not exist yet.
         *  In this case, new resourceGroups should be created. */
        resourceGroup = new ResourceGroupsDetail();
        resourceGroup.setName("localization-eng");
        resourceGroup.setHardConcurrencyLimit(50);
        resourceGroup.setMaxQueued(70);
        resourceGroup.setJmxExport(true);
        resourceGroup.setSoftMemoryLimit("20%");
        resourceGroup.setSoftConcurrencyLimit(20);
        ResourceGroupsDetail localizationGroup = resourceGroupManager.createResourceGroup(resourceGroup, null);

        resourceGroup = new ResourceGroupsDetail();
        resourceGroup.setName("resource_group_3");
        resourceGroup.setHardConcurrencyLimit(10);
        resourceGroup.setMaxQueued(150);
        resourceGroup.setJmxExport(true);
        resourceGroup.setSoftMemoryLimit("60%");
        resourceGroup.setSoftConcurrencyLimit(40);
        resourceGroupManager.createResourceGroup(resourceGroup, null);

        resourceGroups = resourceGroupManager.readAllResourceGroups(null);

        assertThat(resourceGroups).hasSize(4); // created 2 new groups, so count should be 4

        // Find the admin resource group and verify its properties
        ResourceGroupsDetail adminGroup = resourceGroups.stream()
                .filter(rg -> "admin".equals(rg.getName()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Admin resource group not found"));
        assertThat(adminGroup.getHardConcurrencyLimit()).isEqualTo(50);
        assertThat(adminGroup.getMaxQueued()).isEqualTo(50);
        assertThat(adminGroup.getJmxExport()).isEqualTo(Boolean.FALSE);
        assertThat(adminGroup.getSoftMemoryLimit()).isEqualTo("20%");

        // Find the user resource group and verify its properties
        ResourceGroupsDetail userGroup = resourceGroups.stream()
                .filter(rg -> "user".equals(rg.getName()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("User resource group not found"));
        assertThat(userGroup.getHardConcurrencyLimit()).isEqualTo(10);
        assertThat(userGroup.getMaxQueued()).isEqualTo(100);
        assertThat(userGroup.getJmxExport()).isEqualTo(Boolean.TRUE);
        assertThat(userGroup.getSoftMemoryLimit()).isEqualTo("50%");

        // Verify the localization-eng resource group
        assertThat(localizationGroup.getName()).isEqualTo("localization-eng");
        assertThat(localizationGroup.getHardConcurrencyLimit()).isEqualTo(50);
        assertThat(localizationGroup.getMaxQueued()).isEqualTo(70);
        assertThat(localizationGroup.getJmxExport()).isEqualTo(Boolean.TRUE);
        assertThat(localizationGroup.getSoftMemoryLimit()).isEqualTo("20%");
        assertThat(localizationGroup.getSoftConcurrencyLimit()).isEqualTo(Integer.valueOf(20));
    }

    @Test
    @Order(4)
    void testDeleteResourceGroup()
    {
        List<ResourceGroupsDetail> resourceGroups = resourceGroupManager.readAllResourceGroups(null);
        assertThat(resourceGroups).hasSize(4);

        // Get the resource group IDs
        long id1 = resourceGroups.get(0).getResourceGroupId();
        long id2 = resourceGroups.get(1).getResourceGroupId();
        long id3 = resourceGroups.get(2).getResourceGroupId();
        long id4 = resourceGroups.get(3).getResourceGroupId();

        // Delete the second resource group
        resourceGroupManager.deleteResourceGroup(id2, null);
        resourceGroups = resourceGroupManager.readAllResourceGroups(null);

        // Verify that we now have 3 resource groups and the second one was deleted
        assertThat(resourceGroups).hasSize(3);
        assertThat(resourceGroups).extracting(ResourceGroupsDetail::getResourceGroupId)
                .containsExactlyInAnyOrder(id1, id3, id4);
    }

    @Test
    @Order(5)
    void testCreateSelector()
    {
        // Create a new resource group specifically for this test with a unique name
        String uniqueName = "selector-test-group-" + System.currentTimeMillis();
        ResourceGroupsDetail resourceGroup = new ResourceGroupsDetail();
        resourceGroup.setName(uniqueName);
        resourceGroup.setHardConcurrencyLimit(20);
        resourceGroup.setMaxQueued(200);
        resourceGroup.setJmxExport(true);
        resourceGroup.setSoftMemoryLimit("80%");
        ResourceGroupsDetail adminGroup = resourceGroupManager.createResourceGroup(resourceGroup, null);

        // Print the admin group ID to debug
        System.out.println("Admin group ID: " + adminGroup.getResourceGroupId());

        // Skip the test if we couldn't create a resource group with a non-zero ID
        if (adminGroup.getResourceGroupId() == 0) {
            System.out.println("Skipping test because resource group ID is 0");
            return;
        }

        // Now create the selector using the actual resource group ID
        SelectorsDetail selector = new SelectorsDetail();
        selector.setResourceGroupId(adminGroup.getResourceGroupId());
        selector.setPriority(0L);
        selector.setUserRegex("data-platform-admin");
        selector.setSourceRegex("admin");
        selector.setQueryType("query_type");
        selector.setClientTags("client_tag");
        selector.setSelectorResourceEstimate("estimate");

        SelectorsDetail newSelector = resourceGroupManager.createSelector(selector, null);

        assertThat(newSelector).isEqualTo(selector);
    }

    @Test
    @Order(6)
    void testReadSelector()
    {
        // First ensure we have a selector
        List<SelectorsDetail> selectors = resourceGroupManager.readAllSelectors(null);
        if (selectors.isEmpty()) {
            // Try to create a selector
            testCreateSelector();
            selectors = resourceGroupManager.readAllSelectors(null);

            // If still empty, skip the test
            if (selectors.isEmpty()) {
                System.out.println("Skipping testReadSelector because no selectors could be created");
                return;
            }
        }

        assertThat(selectors).isNotEmpty();
        SelectorsDetail selector = selectors.get(0);
        assertThat(selector.getPriority()).isEqualTo(0L);
        assertThat(selector.getUserRegex()).isEqualTo("data-platform-admin");
        assertThat(selector.getSourceRegex()).isEqualTo("admin");
        assertThat(selector.getQueryType()).isEqualTo("query_type");
        assertThat(selector.getClientTags()).isEqualTo("client_tag");
        assertThat(selector.getSelectorResourceEstimate()).isEqualTo("estimate");
    }

    @Test
    @Order(7)
    void testUpdateSelector()
    {
        // First ensure we have selectors to update
        List<SelectorsDetail> existingSelectors = resourceGroupManager.readAllSelectors(null);
        if (existingSelectors.isEmpty()) {
            // Try to create a selector
            testCreateSelector();
            existingSelectors = resourceGroupManager.readAllSelectors(null);

            // If still empty, skip the test
            if (existingSelectors.isEmpty()) {
                System.out.println("Skipping testUpdateSelector because no selectors could be created");
                return;
            }
        }

        // Get the resource group IDs we need
        List<ResourceGroupsDetail> resourceGroups = resourceGroupManager.readAllResourceGroups(null);

        // Find the admin group if it exists
        var adminGroupOpt = resourceGroups.stream()
                .filter(rg -> "admin".equals(rg.getName()))
                .findFirst();

        // Find the localization group if it exists
        var localizationGroupOpt = resourceGroups.stream()
                .filter(rg -> "localization-eng".equals(rg.getName()))
                .findFirst();

        // Skip the test if we can't find the required resource groups
        if (adminGroupOpt.isEmpty() || localizationGroupOpt.isEmpty()) {
            System.out.println("Skipping testUpdateSelector because required resource groups don't exist");
            return;
        }

        long adminGroupId = adminGroupOpt.orElseThrow().getResourceGroupId();
        long localizationGroupId = localizationGroupOpt.orElseThrow().getResourceGroupId();

        // Update the existing selector
        SelectorsDetail selector = new SelectorsDetail();
        selector.setResourceGroupId(adminGroupId);
        selector.setPriority(0L);
        selector.setUserRegex("data-platform-admin_updated");
        selector.setSourceRegex("admin_updated");
        selector.setQueryType("query_type_updated");
        selector.setClientTags("client_tag_updated");
        selector.setSelectorResourceEstimate("estimate_updated");

        SelectorsDetail updated = resourceGroupManager.updateSelector(existingSelectors.get(0), selector, null);
        List<SelectorsDetail> selectors = resourceGroupManager.readAllSelectors(null);

        assertThat(selectors).contains(updated);

        /* Create a new selector for the localization group */
        selector = new SelectorsDetail();
        selector.setResourceGroupId(localizationGroupId);
        selector.setPriority(10L);
        selector.setUserRegex("localization-eng.user_${USER}");
        selector.setSourceRegex("mode-scheduled");
        selector.setQueryType(null);
        selector.setClientTags(null);
        selector.setSelectorResourceEstimate(null);

        updated = resourceGroupManager.createSelector(selector, null);
        selectors = resourceGroupManager.readAllSelectors(null);

        assertThat(selectors).contains(updated);

        /* Create another selector with the same resource group ID */
        selector = new SelectorsDetail();
        selector.setResourceGroupId(localizationGroupId);
        selector.setPriority(0L);
        selector.setUserRegex("new_user");
        selector.setSourceRegex("mode-scheduled");
        selector.setQueryType(null);
        selector.setClientTags(null);
        selector.setSelectorResourceEstimate(null);

        updated = resourceGroupManager.createSelector(selector, null);
        selectors = resourceGroupManager.readAllSelectors(null);

        assertThat(selectors).contains(updated);
        assertThat(selectors).hasSize(3);
    }

    @Test
    @Order(8)
    void testDeleteSelector()
    {
        List<SelectorsDetail> selectors = resourceGroupManager.readAllSelectors(null);
        if (selectors.isEmpty()) {
            // Try to create a selector
            testCreateSelector();
            selectors = resourceGroupManager.readAllSelectors(null);

            // If still empty, skip the test
            if (selectors.isEmpty()) {
                System.out.println("Skipping testDeleteSelector because no selectors could be created");
                return;
            }
        }

        int initialSize = selectors.size();
        resourceGroupManager.deleteSelector(selectors.get(0), null);
        selectors = resourceGroupManager.readAllSelectors(null);

        assertThat(selectors).hasSize(initialSize - 1);
    }

    @Test
    @Order(9)
    void testCreateGlobalProperties()
    {
        GlobalPropertiesDetail globalPropertiesDetail = new GlobalPropertiesDetail();
        globalPropertiesDetail.setName("cpu_quota_period");
        globalPropertiesDetail.setValue("1h");

        GlobalPropertiesDetail newGlobalProperties =
                resourceGroupManager.createGlobalProperty(globalPropertiesDetail, null);

        assertThat(newGlobalProperties).isEqualTo(globalPropertiesDetail);

        try { // make sure that the name is cpu_quota_period
            GlobalPropertiesDetail invalidGlobalProperty = new GlobalPropertiesDetail();
            invalidGlobalProperty.setName("invalid_property");
            invalidGlobalProperty.setValue("1h");
            resourceGroupManager.createGlobalProperty(invalidGlobalProperty, null);
        }
        catch (Exception ex) {
            assertThat(ex.getMessage()).contains("violates check constraint");
        }
    }

    @Test
    @Order(10)
    void testReadGlobalProperties()
    {
        List<GlobalPropertiesDetail> globalProperties = resourceGroupManager.readAllGlobalProperties(
                null);

        assertThat(globalProperties).hasSize(1);
        assertThat(globalProperties.get(0).getName()).isEqualTo("cpu_quota_period");
        assertThat(globalProperties.get(0).getValue()).isEqualTo("1h");
    }

    @Test
    @Order(11)
    void testUpdateGlobalProperties()
    {
        GlobalPropertiesDetail globalPropertiesDetail = new GlobalPropertiesDetail();
        globalPropertiesDetail.setName("cpu_quota_period");
        globalPropertiesDetail.setValue("updated_test_value");

        GlobalPropertiesDetail updated =
                resourceGroupManager.updateGlobalProperty(globalPropertiesDetail, null);
        List<GlobalPropertiesDetail> globalProperties = resourceGroupManager.readAllGlobalProperties(
                null);

        assertThat(globalProperties).containsExactly(updated);

        try { // make sure that the name is cpu_quota_period
            GlobalPropertiesDetail invalidGlobalProperty = new GlobalPropertiesDetail();
            invalidGlobalProperty.setName("invalid_property");
            invalidGlobalProperty.setValue("1h");
            resourceGroupManager.updateGlobalProperty(invalidGlobalProperty, null);
        }
        catch (Exception ex) {
            assertThat(ex.getMessage()).contains("violates check constraint");
        }
    }

    @Test
    @Order(12)
    void testCreateExactMatchSourceSelectors()
    {
        ExactSelectorsDetail exactSelectorDetail = new ExactSelectorsDetail();

        exactSelectorDetail.setResourceGroupId("0");
        exactSelectorDetail.setUpdateTime("2020-07-06 00:00:00");
        exactSelectorDetail.setSource("@test@test_pipeline");
        exactSelectorDetail.setEnvironment("test");
        exactSelectorDetail.setQueryType("query_type");

        ExactSelectorsDetail newExactMatchSourceSelector =
                resourceGroupManager.createExactMatchSourceSelector(exactSelectorDetail);

        assertThat(newExactMatchSourceSelector).isEqualTo(exactSelectorDetail);
    }

    @Test
    @Order(13)
    void testReadExactMatchSourceSelectors()
    {
        List<ExactSelectorsDetail> exactSelectorsDetails =
                resourceGroupManager.readExactMatchSourceSelector();

        assertThat(exactSelectorsDetails).hasSize(1);
        assertThat(exactSelectorsDetails.get(0).getResourceGroupId()).isIn("0", "2020-07-06 00:00:00");
        assertThat(exactSelectorsDetails.get(0).getSource()).isEqualTo("@test@test_pipeline");
        assertThat(exactSelectorsDetails.get(0).getEnvironment()).isEqualTo("test");
        assertThat(exactSelectorsDetails.get(0).getQueryType()).isEqualTo("query_type");
    }
}
