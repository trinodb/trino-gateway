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

import static com.google.common.collect.MoreCollectors.toOptional;
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

        ResourceGroupsDetail adminGroup = resourceGroups.stream()
                .filter(resourceGroup -> "admin".equals(resourceGroup.getName()))
                .collect(toOptional())
                .orElseThrow();
        assertThat(adminGroup.getHardConcurrencyLimit()).isEqualTo(20);
        assertThat(adminGroup.getMaxQueued()).isEqualTo(200);
        assertThat(adminGroup.getJmxExport()).isTrue();
        assertThat(adminGroup.getSoftMemoryLimit()).isEqualTo("80%");
    }

    @Test
    @Order(3)
    void testUpdateResourceGroup()
    {
        List<ResourceGroupsDetail> resourceGroups = resourceGroupManager.readAllResourceGroups(null);
        long adminResourceGroupId = resourceGroups.stream()
                .filter(group -> "admin".equals(group.getName()))
                .collect(toOptional())
                .map(ResourceGroupsDetail::getResourceGroupId)
                .orElseThrow();
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

        assertThat(resourceGroups).hasSize(4);

        ResourceGroupsDetail adminGroup = resourceGroups.stream()
                .filter(group -> "admin".equals(group.getName()))
                .collect(toOptional())
                .orElseThrow();
        assertThat(adminGroup.getHardConcurrencyLimit()).isEqualTo(50);
        assertThat(adminGroup.getMaxQueued()).isEqualTo(50);
        assertThat(adminGroup.getJmxExport()).isFalse();
        assertThat(adminGroup.getSoftMemoryLimit()).isEqualTo("20%");

        ResourceGroupsDetail userGroup = resourceGroups.stream()
                .filter(group -> "user".equals(group.getName()))
                .collect(toOptional())
                .orElseThrow();
        assertThat(userGroup.getHardConcurrencyLimit()).isEqualTo(10);
        assertThat(userGroup.getMaxQueued()).isEqualTo(100);
        assertThat(userGroup.getJmxExport()).isTrue();
        assertThat(userGroup.getSoftMemoryLimit()).isEqualTo("50%");

        assertThat(localizationGroup.getName()).isEqualTo("localization-eng");
        assertThat(localizationGroup.getHardConcurrencyLimit()).isEqualTo(50);
        assertThat(localizationGroup.getMaxQueued()).isEqualTo(70);
        assertThat(localizationGroup.getJmxExport()).isTrue();
        assertThat(localizationGroup.getSoftMemoryLimit()).isEqualTo("20%");
        assertThat(localizationGroup.getSoftConcurrencyLimit()).isEqualTo(Integer.valueOf(20));
    }

    @Test
    @Order(4)
    void testDeleteResourceGroup()
    {
        List<ResourceGroupsDetail> resourceGroups = resourceGroupManager.readAllResourceGroups(null);
        assertThat(resourceGroups).hasSize(4);

        // Delete "resource_group_3" specifically so "admin" and "localization-eng" survive for later tests
        ResourceGroupsDetail groupToDelete = resourceGroups.stream()
                .filter(group -> "resource_group_3".equals(group.getName()))
                .collect(toOptional())
                .orElseThrow();
        resourceGroupManager.deleteResourceGroup(groupToDelete.getResourceGroupId(), null);
        resourceGroups = resourceGroupManager.readAllResourceGroups(null);

        assertThat(resourceGroups).hasSize(3);
        assertThat(resourceGroups).extracting(ResourceGroupsDetail::getName)
                .containsExactlyInAnyOrder("admin", "user", "localization-eng");
    }

    @Test
    @Order(5)
    void testCreateSelector()
    {
        ResourceGroupsDetail resourceGroup = new ResourceGroupsDetail();
        resourceGroup.setName("selector-test-group");
        resourceGroup.setHardConcurrencyLimit(20);
        resourceGroup.setMaxQueued(200);
        resourceGroup.setJmxExport(true);
        resourceGroup.setSoftMemoryLimit("80%");
        resourceGroupManager.createResourceGroup(resourceGroup, null);

        // Read back to get DB-assigned ID
        ResourceGroupsDetail selectorTestGroup = resourceGroupManager.readAllResourceGroups(null).stream()
                .filter(group -> "selector-test-group".equals(group.getName()))
                .collect(toOptional())
                .orElseThrow();

        SelectorsDetail selector = new SelectorsDetail();
        selector.setResourceGroupId(selectorTestGroup.getResourceGroupId());
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
        List<SelectorsDetail> existingSelectors = resourceGroupManager.readAllSelectors(null);
        for (SelectorsDetail existingSelector : existingSelectors) {
            resourceGroupManager.deleteSelector(existingSelector, null);
        }

        List<ResourceGroupsDetail> existingGroups = resourceGroupManager.readAllResourceGroups(null);
        ResourceGroupsDetail testGroup = existingGroups.stream()
                .filter(resourceGroup -> "selector-test-group".equals(resourceGroup.getName()))
                .collect(toOptional())
                .orElseThrow();

        SelectorsDetail selector = new SelectorsDetail();
        selector.setResourceGroupId(testGroup.getResourceGroupId());
        selector.setPriority(0L);
        selector.setUserRegex("data-platform-admin");
        selector.setSourceRegex("admin");
        selector.setQueryType("query_type");
        selector.setClientTags("client_tag");
        selector.setSelectorResourceEstimate("estimate");

        resourceGroupManager.createSelector(selector, null);

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
        List<SelectorsDetail> existingSelectors = resourceGroupManager.readAllSelectors(null);
        for (SelectorsDetail existingSelector : existingSelectors) {
            resourceGroupManager.deleteSelector(existingSelector, null);
        }

        List<ResourceGroupsDetail> resourceGroups = resourceGroupManager.readAllResourceGroups(null);
        ResourceGroupsDetail adminGroup = resourceGroups.stream()
                .filter(resourceGroup -> "admin".equals(resourceGroup.getName()))
                .collect(toOptional())
                .orElseThrow();

        ResourceGroupsDetail localizationGroup = resourceGroups.stream()
                .filter(resourceGroup -> "localization-eng".equals(resourceGroup.getName()))
                .collect(toOptional())
                .orElseThrow();

        SelectorsDetail selector = new SelectorsDetail();
        selector.setResourceGroupId(adminGroup.getResourceGroupId());
        selector.setPriority(0L);
        selector.setUserRegex("data-platform-admin");
        selector.setSourceRegex("admin");
        selector.setQueryType("query_type");
        selector.setClientTags("client_tag");
        selector.setSelectorResourceEstimate("estimate");

        resourceGroupManager.createSelector(selector, null);

        SelectorsDetail updatedSelector = new SelectorsDetail();
        updatedSelector.setResourceGroupId(adminGroup.getResourceGroupId());
        updatedSelector.setPriority(0L);
        updatedSelector.setUserRegex("data-platform-admin_updated");
        updatedSelector.setSourceRegex("admin_updated");
        updatedSelector.setQueryType("query_type_updated");
        updatedSelector.setClientTags("client_tag_updated");
        updatedSelector.setSelectorResourceEstimate("estimate_updated");

        SelectorsDetail updated = resourceGroupManager.updateSelector(selector, updatedSelector, null);

        List<SelectorsDetail> selectors = resourceGroupManager.readAllSelectors(null);
        assertThat(selectors).containsExactly(updated);

        SelectorsDetail selector2 = new SelectorsDetail();
        selector2.setResourceGroupId(localizationGroup.getResourceGroupId());
        selector2.setPriority(10L);
        selector2.setUserRegex("localization-eng.user_${USER}");
        selector2.setSourceRegex("mode-scheduled");
        selector2.setQueryType(null);
        selector2.setClientTags(null);
        selector2.setSelectorResourceEstimate(null);

        SelectorsDetail updated2 = resourceGroupManager.updateSelector(new SelectorsDetail(), selector2, null);

        selectors = resourceGroupManager.readAllSelectors(null);
        assertThat(selectors).hasSize(2)
                .contains(updated, updated2);

        SelectorsDetail selector3 = new SelectorsDetail();
        selector3.setResourceGroupId(localizationGroup.getResourceGroupId());
        selector3.setPriority(0L);
        selector3.setUserRegex("new_user");
        selector3.setSourceRegex("mode-scheduled");
        selector3.setQueryType(null);
        selector3.setClientTags(null);
        selector3.setSelectorResourceEstimate(null);

        SelectorsDetail updated3 = resourceGroupManager.updateSelector(new SelectorsDetail(), selector3, null);

        selectors = resourceGroupManager.readAllSelectors(null);
        assertThat(selectors).hasSize(3)
                .contains(updated, updated2, updated3);
    }

    @Test
    @Order(8)
    void testDeleteSelector()
    {
        List<SelectorsDetail> existingSelectors = resourceGroupManager.readAllSelectors(null);
        for (SelectorsDetail existingSelector : existingSelectors) {
            resourceGroupManager.deleteSelector(existingSelector, null);
        }

        ResourceGroupsDetail resourceGroup = new ResourceGroupsDetail();
        resourceGroup.setName("delete-selector-test-group-1");
        resourceGroup.setHardConcurrencyLimit(20);
        resourceGroup.setMaxQueued(200);
        resourceGroup.setJmxExport(true);
        resourceGroup.setSoftMemoryLimit("80%");
        resourceGroupManager.createResourceGroup(resourceGroup, null);

        resourceGroup = new ResourceGroupsDetail();
        resourceGroup.setName("delete-selector-test-group-2");
        resourceGroup.setHardConcurrencyLimit(20);
        resourceGroup.setMaxQueued(200);
        resourceGroup.setJmxExport(true);
        resourceGroup.setSoftMemoryLimit("80%");
        resourceGroupManager.createResourceGroup(resourceGroup, null);

        resourceGroup = new ResourceGroupsDetail();
        resourceGroup.setName("delete-selector-test-group-3");
        resourceGroup.setHardConcurrencyLimit(20);
        resourceGroup.setMaxQueued(200);
        resourceGroup.setJmxExport(true);
        resourceGroup.setSoftMemoryLimit("80%");
        resourceGroupManager.createResourceGroup(resourceGroup, null);

        // Read back to get DB-assigned IDs
        List<ResourceGroupsDetail> allGroups = resourceGroupManager.readAllResourceGroups(null);
        ResourceGroupsDetail group1 = allGroups.stream()
                .filter(group -> "delete-selector-test-group-1".equals(group.getName()))
                .collect(toOptional())
                .orElseThrow();
        ResourceGroupsDetail group2 = allGroups.stream()
                .filter(group -> "delete-selector-test-group-2".equals(group.getName()))
                .collect(toOptional())
                .orElseThrow();
        ResourceGroupsDetail group3 = allGroups.stream()
                .filter(group -> "delete-selector-test-group-3".equals(group.getName()))
                .collect(toOptional())
                .orElseThrow();

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

        resourceGroupManager.createSelector(selector1, null);
        resourceGroupManager.createSelector(selector2, null);
        resourceGroupManager.createSelector(selector3, null);

        List<SelectorsDetail> selectors = resourceGroupManager.readAllSelectors(null);
        assertThat(selectors).hasSize(3);

        resourceGroupManager.deleteSelector(selector1, null);

        selectors = resourceGroupManager.readAllSelectors(null);
        assertThat(selectors).hasSize(2);

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
