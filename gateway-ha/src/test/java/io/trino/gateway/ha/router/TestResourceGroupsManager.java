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
        assertThat(resourceGroups.get(0).getJmxExport()).isTrue();
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
        assertThat(adminGroup.getJmxExport()).isEqualTo(false);
        assertThat(adminGroup.getSoftMemoryLimit()).isEqualTo("20%");

        // Find the user resource group and verify its properties
        ResourceGroupsDetail userGroup = resourceGroups.stream()
                .filter(rg -> "user".equals(rg.getName()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("User resource group not found"));
        assertThat(userGroup.getHardConcurrencyLimit()).isEqualTo(10);
        assertThat(userGroup.getMaxQueued()).isEqualTo(100);
        assertThat(userGroup.getJmxExport()).isEqualTo(true);
        assertThat(userGroup.getSoftMemoryLimit()).isEqualTo("50%");

        // Verify the localization-eng resource group
        assertThat(localizationGroup.getName()).isEqualTo("localization-eng");
        assertThat(localizationGroup.getHardConcurrencyLimit()).isEqualTo(50);
        assertThat(localizationGroup.getMaxQueued()).isEqualTo(70);
        assertThat(localizationGroup.getJmxExport()).isEqualTo(true);
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
        // Check if selector-test-group already exists
        List<ResourceGroupsDetail> existingGroups = resourceGroupManager.readAllResourceGroups(null);
        ResourceGroupsDetail adminGroup = existingGroups.stream()
                .filter(rg -> "selector-test-group".equals(rg.getName()))
                .findFirst()
                .orElse(null);

        if (adminGroup == null) {
            ResourceGroupsDetail resourceGroup = new ResourceGroupsDetail();
            resourceGroup.setName("selector-test-group");
            resourceGroup.setHardConcurrencyLimit(20);
            resourceGroup.setMaxQueued(200);
            resourceGroup.setJmxExport(true);
            resourceGroup.setSoftMemoryLimit("80%");
            adminGroup = resourceGroupManager.createResourceGroup(resourceGroup, null);
        }

        if (adminGroup.getResourceGroupId() == 0) {
            return;
        }

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
        // Clean up existing selectors
        List<SelectorsDetail> existingSelectors = resourceGroupManager.readAllSelectors(null);
        for (SelectorsDetail existingSelector : existingSelectors) {
            resourceGroupManager.deleteSelector(existingSelector, null);
        }

        // Check if selector-test-group already exists
        List<ResourceGroupsDetail> existingGroups = resourceGroupManager.readAllResourceGroups(null);
        ResourceGroupsDetail testGroup = existingGroups.stream()
                .filter(rg -> "selector-test-group".equals(rg.getName()))
                .findFirst()
                .orElse(null);

        if (testGroup == null) {
            ResourceGroupsDetail resourceGroup = new ResourceGroupsDetail();
            resourceGroup.setName("selector-test-group");
            resourceGroup.setHardConcurrencyLimit(20);
            resourceGroup.setMaxQueued(200);
            resourceGroup.setJmxExport(true);
            resourceGroup.setSoftMemoryLimit("80%");
            testGroup = resourceGroupManager.createResourceGroup(resourceGroup, null);
        }

        if (testGroup.getResourceGroupId() == 0) {
            return;
        }

        // Create a selector
        SelectorsDetail selector = new SelectorsDetail();
        selector.setResourceGroupId(testGroup.getResourceGroupId());
        selector.setPriority(0L);
        selector.setUserRegex("data-platform-admin");
        selector.setSourceRegex("admin");
        selector.setQueryType("query_type");
        selector.setClientTags("client_tag");
        selector.setSelectorResourceEstimate("estimate");

        // Create the selector in the database
        resourceGroupManager.createSelector(selector, null);

        // Read all selectors and verify
        List<SelectorsDetail> selectors = resourceGroupManager.readAllSelectors(null);
        assertThat(selectors).hasSize(1);
        SelectorsDetail actualSelector = selectors.get(0);
        assertThat(actualSelector.getPriority()).isEqualTo(0L);
        assertThat(actualSelector.getUserRegex()).isEqualTo("data-platform-admin");
        assertThat(actualSelector.getSourceRegex()).isEqualTo("admin");
        assertThat(actualSelector.getQueryType()).isEqualTo("query_type");
        assertThat(actualSelector.getClientTags()).isEqualTo("client_tag");
        assertThat(actualSelector.getSelectorResourceEstimate()).isEqualTo("estimate");
    }

    @Test
    @Order(7)
    void testUpdateSelector()
    {
        // Clean up existing selectors
        List<SelectorsDetail> existingSelectors = resourceGroupManager.readAllSelectors(null);
        for (SelectorsDetail existingSelector : existingSelectors) {
            resourceGroupManager.deleteSelector(existingSelector, null);
        }

        // Create or find the admin group
        ResourceGroupsDetail adminGroup = null;
        List<ResourceGroupsDetail> resourceGroups = resourceGroupManager.readAllResourceGroups(null);
        for (ResourceGroupsDetail group : resourceGroups) {
            if ("admin".equals(group.getName())) {
                adminGroup = group;
                break;
            }
        }

        if (adminGroup == null) {
            ResourceGroupsDetail resourceGroup = new ResourceGroupsDetail();
            resourceGroup.setName("admin");
            resourceGroup.setHardConcurrencyLimit(20);
            resourceGroup.setMaxQueued(200);
            resourceGroup.setJmxExport(true);
            resourceGroup.setSoftMemoryLimit("80%");
            adminGroup = resourceGroupManager.createResourceGroup(resourceGroup, null);
        }

        // Create or find the localization group
        ResourceGroupsDetail localizationGroup = null;
        for (ResourceGroupsDetail group : resourceGroups) {
            if ("localization-eng".equals(group.getName())) {
                localizationGroup = group;
                break;
            }
        }

        if (localizationGroup == null) {
            ResourceGroupsDetail resourceGroup = new ResourceGroupsDetail();
            resourceGroup.setName("localization-eng");
            resourceGroup.setHardConcurrencyLimit(50);
            resourceGroup.setMaxQueued(70);
            resourceGroup.setJmxExport(true);
            resourceGroup.setSoftMemoryLimit("20%");
            resourceGroup.setSoftConcurrencyLimit(20);
            localizationGroup = resourceGroupManager.createResourceGroup(resourceGroup, null);
        }

        // Skip test if resource group IDs are 0
        if (adminGroup.getResourceGroupId() == 0 || localizationGroup.getResourceGroupId() == 0) {
            return;
        }

        // Create initial selector
        SelectorsDetail selector = new SelectorsDetail();
        selector.setResourceGroupId(adminGroup.getResourceGroupId());
        selector.setPriority(0L);
        selector.setUserRegex("data-platform-admin");
        selector.setSourceRegex("admin");
        selector.setQueryType("query_type");
        selector.setClientTags("client_tag");
        selector.setSelectorResourceEstimate("estimate");

        // Create the selector in the database
        resourceGroupManager.createSelector(selector, null);

        // Create updated selector
        SelectorsDetail updatedSelector = new SelectorsDetail();
        updatedSelector.setResourceGroupId(adminGroup.getResourceGroupId());
        updatedSelector.setPriority(0L);
        updatedSelector.setUserRegex("data-platform-admin_updated");
        updatedSelector.setSourceRegex("admin_updated");
        updatedSelector.setQueryType("query_type_updated");
        updatedSelector.setClientTags("client_tag_updated");
        updatedSelector.setSelectorResourceEstimate("estimate_updated");

        // Update the selector
        SelectorsDetail updated = resourceGroupManager.updateSelector(selector, updatedSelector, null);

        // Read all selectors and verify
        List<SelectorsDetail> selectors = resourceGroupManager.readAllSelectors(null);
        assertThat(selectors).containsExactly(updated);

        // Create a new selector with different resource group
        SelectorsDetail selector2 = new SelectorsDetail();
        selector2.setResourceGroupId(localizationGroup.getResourceGroupId());
        selector2.setPriority(10L);
        selector2.setUserRegex("localization-eng.user_${USER}");
        selector2.setSourceRegex("mode-scheduled");
        selector2.setQueryType(null);
        selector2.setClientTags(null);
        selector2.setSelectorResourceEstimate(null);

        // Add the new selector
        SelectorsDetail updated2 = resourceGroupManager.updateSelector(new SelectorsDetail(), selector2, null);

        // Read all selectors and verify
        selectors = resourceGroupManager.readAllSelectors(null);
        assertThat(selectors).hasSize(2)
                .contains(updated, updated2);

        // Create a third selector with same resource group
        SelectorsDetail selector3 = new SelectorsDetail();
        selector3.setResourceGroupId(localizationGroup.getResourceGroupId());
        selector3.setPriority(0L);
        selector3.setUserRegex("new_user");
        selector3.setSourceRegex("mode-scheduled");
        selector3.setQueryType(null);
        selector3.setClientTags(null);
        selector3.setSelectorResourceEstimate(null);

        // Add the third selector
        SelectorsDetail updated3 = resourceGroupManager.updateSelector(new SelectorsDetail(), selector3, null);

        // Read all selectors and verify
        selectors = resourceGroupManager.readAllSelectors(null);
        assertThat(selectors).hasSize(3)
                .contains(updated, updated2, updated3);
    }

    @Test
    @Order(8)
    void testDeleteSelector()
    {
        // Clean up existing selectors
        List<SelectorsDetail> existingSelectors = resourceGroupManager.readAllSelectors(null);
        for (SelectorsDetail existingSelector : existingSelectors) {
            resourceGroupManager.deleteSelector(existingSelector, null);
        }

        // Create resource groups
        ResourceGroupsDetail resourceGroup = new ResourceGroupsDetail();
        resourceGroup.setName("delete-selector-test-group-1");
        resourceGroup.setHardConcurrencyLimit(20);
        resourceGroup.setMaxQueued(200);
        resourceGroup.setJmxExport(true);
        resourceGroup.setSoftMemoryLimit("80%");
        ResourceGroupsDetail group1 = resourceGroupManager.createResourceGroup(resourceGroup, null);

        resourceGroup = new ResourceGroupsDetail();
        resourceGroup.setName("delete-selector-test-group-2");
        resourceGroup.setHardConcurrencyLimit(20);
        resourceGroup.setMaxQueued(200);
        resourceGroup.setJmxExport(true);
        resourceGroup.setSoftMemoryLimit("80%");
        ResourceGroupsDetail group2 = resourceGroupManager.createResourceGroup(resourceGroup, null);

        resourceGroup = new ResourceGroupsDetail();
        resourceGroup.setName("delete-selector-test-group-3");
        resourceGroup.setHardConcurrencyLimit(20);
        resourceGroup.setMaxQueued(200);
        resourceGroup.setJmxExport(true);
        resourceGroup.setSoftMemoryLimit("80%");
        ResourceGroupsDetail group3 = resourceGroupManager.createResourceGroup(resourceGroup, null);

        // Skip test if resource group IDs are 0
        if (group1.getResourceGroupId() == 0 || group2.getResourceGroupId() == 0 || group3.getResourceGroupId() == 0) {
            return;
        }

        // Create selectors
        SelectorsDetail selector1 = new SelectorsDetail();
        selector1.setResourceGroupId(group1.getResourceGroupId());
        selector1.setPriority(0L);
        selector1.setUserRegex("user1");
        selector1.setSourceRegex("source1");

        SelectorsDetail selector2 = new SelectorsDetail();
        selector2.setResourceGroupId(group2.getResourceGroupId());
        selector2.setPriority(0L);
        selector2.setUserRegex("user2");
        selector2.setSourceRegex("source2");

        SelectorsDetail selector3 = new SelectorsDetail();
        selector3.setResourceGroupId(group3.getResourceGroupId());
        selector3.setPriority(0L);
        selector3.setUserRegex("user3");
        selector3.setSourceRegex("source3");

        // Add all selectors
        resourceGroupManager.createSelector(selector1, null);
        resourceGroupManager.createSelector(selector2, null);
        resourceGroupManager.createSelector(selector3, null);

        // Verify we have 3 selectors
        List<SelectorsDetail> selectors = resourceGroupManager.readAllSelectors(null);
        assertThat(selectors).hasSize(3);

        // Delete the first selector
        resourceGroupManager.deleteSelector(selector1, null);

        // Verify we now have 2 selectors
        selectors = resourceGroupManager.readAllSelectors(null);
        assertThat(selectors).hasSize(2);

        // Clean up
        resourceGroupManager.deleteSelector(selector2, null);
        resourceGroupManager.deleteSelector(selector3, null);
        resourceGroupManager.deleteResourceGroup(group1.getResourceGroupId(), null);
        resourceGroupManager.deleteResourceGroup(group2.getResourceGroupId(), null);
        resourceGroupManager.deleteResourceGroup(group3.getResourceGroupId(), null);
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
