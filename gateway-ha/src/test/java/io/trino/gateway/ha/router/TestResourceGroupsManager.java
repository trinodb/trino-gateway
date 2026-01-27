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
import static io.trino.gateway.ha.TestingJdbcConnectionManager.dataStoreConfig;
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
        JdbcConnectionManager connectionManager = createTestingJdbcConnectionManager(dataStoreConfig());
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
        ResourceGroupsDetail resourceGroup = new ResourceGroupsDetail();
        resourceGroup.setResourceGroupId(1L);
        resourceGroup.setName("admin");
        resourceGroup.setHardConcurrencyLimit(50);
        resourceGroup.setMaxQueued(50);
        resourceGroup.setJmxExport(false);
        resourceGroup.setSoftMemoryLimit("20%");

        ResourceGroupsDetail updated = resourceGroupManager.updateResourceGroup(resourceGroup, null);
        List<ResourceGroupsDetail> resourceGroups = resourceGroupManager.readAllResourceGroups(null);
        assertThat(resourceGroups).contains(updated);

        /* Update resourceGroups that do not exist yet.
         *  In this case, new resourceGroups should be created. */
        resourceGroup.setResourceGroupId(3L);
        resourceGroup.setName("localization-eng");
        resourceGroup.setHardConcurrencyLimit(50);
        resourceGroup.setMaxQueued(70);
        resourceGroup.setJmxExport(true);
        resourceGroup.setSoftMemoryLimit("20%");
        resourceGroup.setSoftConcurrencyLimit(20);
        resourceGroupManager.updateResourceGroup(resourceGroup, null);

        resourceGroup.setResourceGroupId(4L);
        resourceGroup.setName("resource_group_3");
        resourceGroup.setHardConcurrencyLimit(10);
        resourceGroup.setMaxQueued(150);
        resourceGroup.setJmxExport(true);
        resourceGroup.setSoftMemoryLimit("60%");
        resourceGroup.setSoftConcurrencyLimit(40);
        resourceGroupManager.updateResourceGroup(resourceGroup, null);

        resourceGroups = resourceGroupManager.readAllResourceGroups(null);

        assertThat(resourceGroups).hasSize(4); // updated 2 non-existing groups, so count should be 4

        assertThat(resourceGroups.get(0).getResourceGroupId()).isEqualTo(1L);
        assertThat(resourceGroups.get(0).getName()).isEqualTo("admin");
        assertThat(resourceGroups.get(0).getHardConcurrencyLimit()).isEqualTo(50);
        assertThat(resourceGroups.get(0).getMaxQueued()).isEqualTo(50);
        assertThat(resourceGroups.get(0).getJmxExport()).isFalse();
        assertThat(resourceGroups.get(0).getSoftMemoryLimit()).isEqualTo("20%");

        assertThat(resourceGroups.get(1).getResourceGroupId()).isEqualTo(2L);
        assertThat(resourceGroups.get(1).getName()).isEqualTo("user");
        assertThat(resourceGroups.get(1).getHardConcurrencyLimit()).isEqualTo(10);
        assertThat(resourceGroups.get(1).getMaxQueued()).isEqualTo(100);
        assertThat(resourceGroups.get(1).getJmxExport()).isTrue();
        assertThat(resourceGroups.get(1).getSoftMemoryLimit()).isEqualTo("50%");

        assertThat(resourceGroups.get(2).getResourceGroupId()).isEqualTo(3L);
        assertThat(resourceGroups.get(2).getName()).isEqualTo("localization-eng");
        assertThat(resourceGroups.get(2).getHardConcurrencyLimit()).isEqualTo(50);
        assertThat(resourceGroups.get(2).getMaxQueued()).isEqualTo(70);
        assertThat(resourceGroups.get(2).getJmxExport()).isTrue();
        assertThat(resourceGroups.get(2).getSoftMemoryLimit()).isEqualTo("20%");
        assertThat(resourceGroups.get(2).getSoftConcurrencyLimit()).isEqualTo(Integer.valueOf(20));
    }

    @Test
    @Order(4)
    void testDeleteResourceGroup()
    {
        List<ResourceGroupsDetail> resourceGroups = resourceGroupManager.readAllResourceGroups(null);
        assertThat(resourceGroups).hasSize(4);

        assertThat(resourceGroups.get(0).getResourceGroupId()).isEqualTo(1L);
        assertThat(resourceGroups.get(1).getResourceGroupId()).isEqualTo(2L);
        assertThat(resourceGroups.get(2).getResourceGroupId()).isEqualTo(3L);
        assertThat(resourceGroups.get(3).getResourceGroupId()).isEqualTo(4L);

        resourceGroupManager.deleteResourceGroup(resourceGroups.get(1).getResourceGroupId(), null);
        resourceGroups = resourceGroupManager.readAllResourceGroups(null);

        assertThat(resourceGroups).hasSize(3);
        assertThat(resourceGroups.get(0).getResourceGroupId()).isEqualTo(1L);
        assertThat(resourceGroups.get(1).getResourceGroupId()).isEqualTo(3L);
        assertThat(resourceGroups.get(2).getResourceGroupId()).isEqualTo(4L);
    }

    @Test
    @Order(5)
    void testCreateSelector()
    {
        SelectorsDetail selector = new SelectorsDetail();
        selector.setResourceGroupId(1L);
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
        List<SelectorsDetail> selectors = resourceGroupManager.readAllSelectors(null);

        assertThat(selectors).hasSize(1);
        assertThat(selectors.get(0).getResourceGroupId()).isEqualTo(1L);
        assertThat(selectors.get(0).getPriority()).isEqualTo(0L);
        assertThat(selectors.get(0).getUserRegex()).isEqualTo("data-platform-admin");
        assertThat(selectors.get(0).getSourceRegex()).isEqualTo("admin");
        assertThat(selectors.get(0).getQueryType()).isEqualTo("query_type");
        assertThat(selectors.get(0).getClientTags()).isEqualTo("client_tag");
        assertThat(selectors.get(0).getSelectorResourceEstimate()).isEqualTo("estimate");
    }

    @Test
    @Order(7)
    void testUpdateSelector()
    {
        SelectorsDetail selector = new SelectorsDetail();

        selector.setResourceGroupId(1L);
        selector.setPriority(0L);
        selector.setUserRegex("data-platform-admin_updated");
        selector.setSourceRegex("admin_updated");
        selector.setQueryType("query_type_updated");
        selector.setClientTags("client_tag_updated");
        selector.setSelectorResourceEstimate("estimate_updated");

        List<SelectorsDetail> selectors = resourceGroupManager.readAllSelectors(null);
        SelectorsDetail updated = resourceGroupManager.updateSelector(selectors.get(0), selector, null);
        selectors = resourceGroupManager.readAllSelectors(null);

        assertThat(selectors).containsExactly(updated);

        /* Update selectors that do not exist yet.
         *  In this case, a new selector should be created. */
        selector.setResourceGroupId(3L);
        selector.setPriority(10L);
        selector.setUserRegex("localization-eng.user_${USER}");
        selector.setSourceRegex("mode-scheduled");
        selector.setQueryType(null);
        selector.setClientTags(null);
        selector.setSelectorResourceEstimate(null);

        updated = resourceGroupManager.updateSelector(new SelectorsDetail(), selector, null);
        selectors = resourceGroupManager.readAllSelectors(null);

        assertThat(selectors).hasSize(2)
                .element(1).isEqualTo(updated);

        /* Create selector with an already existing resourceGroupId.
         *  In this case, new selector should be created. */
        selector.setResourceGroupId(3L);
        selector.setPriority(0L);
        selector.setUserRegex("new_user");
        selector.setSourceRegex("mode-scheduled");
        selector.setQueryType(null);
        selector.setClientTags(null);
        selector.setSelectorResourceEstimate(null);

        updated = resourceGroupManager.updateSelector(new SelectorsDetail(), selector, null);
        selectors = resourceGroupManager.readAllSelectors(null);

        assertThat(selectors).hasSize(3)
                .element(2).isEqualTo(updated);
    }

    @Test
    @Order(8)
    void testDeleteSelector()
    {
        List<SelectorsDetail> selectors = resourceGroupManager.readAllSelectors(null);
        assertThat(selectors).hasSize(3);
        assertThat(selectors.get(0).getResourceGroupId()).isEqualTo(1L);
        resourceGroupManager.deleteSelector(selectors.get(0), null);
        selectors = resourceGroupManager.readAllSelectors(null);

        assertThat(selectors).hasSize(2);
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
            assertThat(ex.getCause())
                    .isInstanceOf(org.h2.jdbc.JdbcSQLIntegrityConstraintViolationException.class)
                    .hasMessageStartingWith("Check constraint violation:");
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
            assertThat(ex.getCause())
                    .isInstanceOf(org.h2.jdbc.JdbcSQLIntegrityConstraintViolationException.class)
                    .hasMessageStartingWith("Check constraint violation:");
        }
    }

    @Test
    @Order(12)
    void testCreateExactMatchSourceSelectors()
    {
        ExactSelectorsDetail exactSelectorDetail = new ExactSelectorsDetail();

        exactSelectorDetail.setResourceGroupId("0");
        exactSelectorDetail.setUpdateTime("2020-07-06");
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
        assertThat(exactSelectorsDetails.get(0).getResourceGroupId()).isEqualTo("0");
        assertThat(exactSelectorsDetails.get(0).getSource()).isEqualTo("@test@test_pipeline");
        assertThat(exactSelectorsDetails.get(0).getEnvironment()).isEqualTo("test");
        assertThat(exactSelectorsDetails.get(0).getQueryType()).isEqualTo("query_type");
    }
}
