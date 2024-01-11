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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.MethodOrderer.OrderAnnotation;

@TestMethodOrder(OrderAnnotation.class)
@TestInstance(Lifecycle.PER_CLASS)
public class TestResourceGroupsManager
{
    public ResourceGroupsManager resourceGroupManager;

    @BeforeAll
    public void setUp()
    {
        JdbcConnectionManager connectionManager = createTestingJdbcConnectionManager();
        resourceGroupManager = new HaResourceGroupsManager(connectionManager);
    }

    @Test
    @Order(1)
    public void testCreateResourceGroup()
    {
        ResourceGroupsDetail resourceGroup = new ResourceGroupsDetail();

        resourceGroup.setResourceGroupId(0L);
        resourceGroup.setName("admin");
        resourceGroup.setHardConcurrencyLimit(20);
        resourceGroup.setMaxQueued(200);
        resourceGroup.setJmxExport(true);
        resourceGroup.setSoftMemoryLimit("80%");

        ResourceGroupsDetail newResourceGroup = resourceGroupManager.createResourceGroup(resourceGroup,
                null);

        assertEquals(resourceGroup, newResourceGroup);
    }

    @Test
    @Order(2)
    public void testReadResourceGroup()
    {
        List<ResourceGroupsDetail> resourceGroups = resourceGroupManager.readAllResourceGroups(null);
        assertThat(resourceGroups).hasSize(1);

        assertEquals(0L, resourceGroups.get(0).getResourceGroupId());
        assertEquals("admin", resourceGroups.get(0).getName());
        assertEquals(20, resourceGroups.get(0).getHardConcurrencyLimit());
        assertEquals(200, resourceGroups.get(0).getMaxQueued());
        assertEquals(Boolean.TRUE, resourceGroups.get(0).getJmxExport());
        assertEquals("80%", resourceGroups.get(0).getSoftMemoryLimit());
    }

    @Test
    @Order(3)
    public void testUpdateResourceGroup()
    {
        ResourceGroupsDetail resourceGroup = new ResourceGroupsDetail();
        resourceGroup.setResourceGroupId(0L);
        resourceGroup.setName("admin");
        resourceGroup.setHardConcurrencyLimit(50);
        resourceGroup.setMaxQueued(50);
        resourceGroup.setJmxExport(false);
        resourceGroup.setSoftMemoryLimit("20%");

        ResourceGroupsDetail updated = resourceGroupManager.updateResourceGroup(resourceGroup, null);
        List<ResourceGroupsDetail> resourceGroups = resourceGroupManager.readAllResourceGroups(null);
        assertThat(resourceGroups).containsExactly(updated);

        /* Update resourceGroups that do not exist yet.
         *  In this case, new resourceGroups should be created. */
        resourceGroup.setResourceGroupId(1L);
        resourceGroup.setName("localization-eng");
        resourceGroup.setHardConcurrencyLimit(50);
        resourceGroup.setMaxQueued(70);
        resourceGroup.setJmxExport(true);
        resourceGroup.setSoftMemoryLimit("20%");
        resourceGroup.setSoftConcurrencyLimit(20);
        resourceGroupManager.updateResourceGroup(resourceGroup, null);

        resourceGroup.setResourceGroupId(3L);
        resourceGroup.setName("resource_group_3");
        resourceGroup.setHardConcurrencyLimit(10);
        resourceGroup.setMaxQueued(150);
        resourceGroup.setJmxExport(true);
        resourceGroup.setSoftMemoryLimit("60%");
        resourceGroup.setSoftConcurrencyLimit(40);
        resourceGroupManager.updateResourceGroup(resourceGroup, null);

        resourceGroups = resourceGroupManager.readAllResourceGroups(null);

        assertThat(resourceGroups).hasSize(3); // updated 2 non-existing groups, so count should be 3

        assertEquals(0L, resourceGroups.get(0).getResourceGroupId());
        assertEquals("admin", resourceGroups.get(0).getName());
        assertEquals(50, resourceGroups.get(0).getHardConcurrencyLimit());
        assertEquals(50, resourceGroups.get(0).getMaxQueued());
        assertEquals(Boolean.FALSE, resourceGroups.get(0).getJmxExport());
        assertEquals("20%", resourceGroups.get(0).getSoftMemoryLimit());

        assertEquals(1L, resourceGroups.get(1).getResourceGroupId());
        assertEquals("localization-eng", resourceGroups.get(1).getName());
        assertEquals(50, resourceGroups.get(1).getHardConcurrencyLimit());
        assertEquals(70, resourceGroups.get(1).getMaxQueued());
        assertEquals(Boolean.TRUE, resourceGroups.get(1).getJmxExport());
        assertEquals("20%", resourceGroups.get(1).getSoftMemoryLimit());
        assertEquals(Integer.valueOf(20), resourceGroups.get(1).getSoftConcurrencyLimit());
    }

    @Test
    @Order(4)
    public void testDeleteResourceGroup()
    {
        List<ResourceGroupsDetail> resourceGroups = resourceGroupManager.readAllResourceGroups(null);
        assertThat(resourceGroups).hasSize(3);

        assertEquals(0L, resourceGroups.get(0).getResourceGroupId());
        assertEquals(1L, resourceGroups.get(1).getResourceGroupId());
        assertEquals(3L, resourceGroups.get(2).getResourceGroupId());

        resourceGroupManager.deleteResourceGroup(resourceGroups.get(1).getResourceGroupId(), null);
        resourceGroups = resourceGroupManager.readAllResourceGroups(null);

        assertEquals(2, resourceGroups.size());
        assertEquals(0L, resourceGroups.get(0).getResourceGroupId());
        assertEquals(3L, resourceGroups.get(1).getResourceGroupId());
    }

    @Test
    @Order(5)
    public void testCreateSelector()
    {
        SelectorsDetail selector = new SelectorsDetail();
        selector.setResourceGroupId(0L);
        selector.setPriority(0L);
        selector.setUserRegex("data-platform-admin");
        selector.setSourceRegex("admin");
        selector.setQueryType("query_type");
        selector.setClientTags("client_tag");
        selector.setSelectorResourceEstimate("estimate");

        SelectorsDetail newSelector = resourceGroupManager.createSelector(selector, null);

        assertEquals(selector, newSelector);
    }

    @Test
    @Order(6)
    public void testReadSelector()
    {
        List<SelectorsDetail> selectors = resourceGroupManager.readAllSelectors(null);

        assertThat(selectors).hasSize(1);
        assertEquals(0L, selectors.get(0).getResourceGroupId());
        assertEquals(0L, selectors.get(0).getPriority());
        assertEquals("data-platform-admin", selectors.get(0).getUserRegex());
        assertEquals("admin", selectors.get(0).getSourceRegex());
        assertEquals("query_type", selectors.get(0).getQueryType());
        assertEquals("client_tag", selectors.get(0).getClientTags());
        assertEquals("estimate", selectors.get(0).getSelectorResourceEstimate());
    }

    @Test
    @Order(7)
    public void testUpdateSelector()
    {
        SelectorsDetail selector = new SelectorsDetail();

        selector.setResourceGroupId(0L);
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
    public void testDeleteSelector()
    {
        List<SelectorsDetail> selectors = resourceGroupManager.readAllSelectors(null);
        assertThat(selectors).hasSize(3);
        assertEquals(0L, selectors.get(0).getResourceGroupId());
        resourceGroupManager.deleteSelector(selectors.get(0), null);
        selectors = resourceGroupManager.readAllSelectors(null);

        assertThat(selectors).hasSize(2);
    }

    @Test
    @Order(9)
    public void testCreateGlobalProperties()
    {
        GlobalPropertiesDetail globalPropertiesDetail = new GlobalPropertiesDetail();
        globalPropertiesDetail.setName("cpu_quota_period");
        globalPropertiesDetail.setValue("1h");

        GlobalPropertiesDetail newGlobalProperties =
                resourceGroupManager.createGlobalProperty(globalPropertiesDetail, null);

        assertEquals(globalPropertiesDetail, newGlobalProperties);

        try { // make sure that the name is cpu_quota_period
            GlobalPropertiesDetail invalidGlobalProperty = new GlobalPropertiesDetail();
            invalidGlobalProperty.setName("invalid_property");
            invalidGlobalProperty.setValue("1h");
            resourceGroupManager.createGlobalProperty(invalidGlobalProperty, null);
        }
        catch (Exception ex) {
            assertTrue(ex.getCause() instanceof org.h2.jdbc.JdbcSQLException);
            assertTrue(ex.getCause().getMessage().startsWith("Check constraint violation:"));
        }
    }

    @Test
    @Order(10)
    public void testReadGlobalProperties()
    {
        List<GlobalPropertiesDetail> globalProperties = resourceGroupManager.readAllGlobalProperties(
                null);

        assertThat(globalProperties).hasSize(1);
        assertEquals("cpu_quota_period", globalProperties.get(0).getName());
        assertEquals("1h", globalProperties.get(0).getValue());
    }

    @Test
    @Order(11)
    public void testUpdateGlobalProperties()
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
            assertTrue(ex.getCause() instanceof org.h2.jdbc.JdbcSQLException);
            assertTrue(ex.getCause().getMessage().startsWith("Check constraint violation:"));
        }
    }

    @Test
    @Order(12)
    public void testCreateExactMatchSourceSelectors()
    {
        ExactSelectorsDetail exactSelectorDetail = new ExactSelectorsDetail();

        exactSelectorDetail.setResourceGroupId("0");
        exactSelectorDetail.setUpdateTime("2020-07-06");
        exactSelectorDetail.setSource("@test@test_pipeline");
        exactSelectorDetail.setEnvironment("test");
        exactSelectorDetail.setQueryType("query_type");

        ExactSelectorsDetail newExactMatchSourceSelector =
                resourceGroupManager.createExactMatchSourceSelector(exactSelectorDetail);

        assertEquals(exactSelectorDetail, newExactMatchSourceSelector);
    }

    @Test
    @Order(13)
    public void testReadExactMatchSourceSelectors()
    {
        List<ExactSelectorsDetail> exactSelectorsDetails =
                resourceGroupManager.readExactMatchSourceSelector();

        assertEquals(1, exactSelectorsDetails.size());
        assertEquals("0", exactSelectorsDetails.get(0).getResourceGroupId());
        assertEquals("@test@test_pipeline", exactSelectorsDetails.get(0).getSource());
        assertEquals("test", exactSelectorsDetails.get(0).getEnvironment());
        assertEquals("query_type", exactSelectorsDetails.get(0).getQueryType());

        ExactSelectorsDetail exactSelector =
                resourceGroupManager.getExactMatchSourceSelector(exactSelectorsDetails.get(0));

        assertEquals("0", exactSelector.getResourceGroupId());
        assertEquals("@test@test_pipeline", exactSelector.getSource());
        assertEquals("test", exactSelector.getEnvironment());
        assertEquals("query_type", exactSelector.getQueryType());
    }
}
