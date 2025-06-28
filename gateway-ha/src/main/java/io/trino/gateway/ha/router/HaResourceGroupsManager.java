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

import com.google.common.collect.ImmutableList;
import io.trino.gateway.ha.persistence.JdbcConnectionManager;
import io.trino.gateway.ha.persistence.dao.ExactMatchSourceSelectors;
import io.trino.gateway.ha.persistence.dao.ExactMatchSourceSelectorsDao;
import io.trino.gateway.ha.persistence.dao.ResourceGroups;
import io.trino.gateway.ha.persistence.dao.ResourceGroupsDao;
import io.trino.gateway.ha.persistence.dao.ResourceGroupsGlobalProperties;
import io.trino.gateway.ha.persistence.dao.ResourceGroupsGlobalPropertiesDao;
import io.trino.gateway.ha.persistence.dao.Selectors;
import io.trino.gateway.ha.persistence.dao.SelectorsDao;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.google.common.collect.ImmutableList.toImmutableList;

public class HaResourceGroupsManager
        implements ResourceGroupsManager
{
    private final JdbcConnectionManager connectionManager;
    private final ExactMatchSourceSelectorsDao exactMatchSourceSelectorsDao;

    public HaResourceGroupsManager(JdbcConnectionManager connectionManager)
    {
        this.connectionManager = connectionManager;
        this.exactMatchSourceSelectorsDao = connectionManager.getJdbi().onDemand(ExactMatchSourceSelectorsDao.class);
    }

    /**
     * Creates and returns a resource group with the given parameters.
     *
     * @return the created ResourceGroupDetail object
     */
    @Override
    public ResourceGroupsDetail createResourceGroup(ResourceGroupsDetail resourceGroup,
            @Nullable String routingGroupDatabase)
    {
        getResourceGroupsDao(routingGroupDatabase).create(resourceGroup);
        return resourceGroup;
    }

    /**
     * Retrieves a list of all existing resource groups for a specified database.
     *
     * @return all existing resource groups as a list of ResourceGroupDetail objects
     */
    @Override
    public List<ResourceGroupsDetail> readAllResourceGroups(@Nullable String routingGroupDatabase)
    {
        List<ResourceGroups> resourceGroups = getResourceGroupsDao(routingGroupDatabase).findAll();
        return upcastResourceGroups(resourceGroups);
    }

    /**
     * Retrieves a specific resource group based on its resourceGroupId for a specific database.
     *
     * @return a specific resource group as a ResourceGroupDetail object
     */
    @Override
    public List<ResourceGroupsDetail> readResourceGroup(long resourceGroupId,
            @Nullable String routingGroupDatabase)
    {
        List<ResourceGroups> resourceGroups = getResourceGroupsDao(routingGroupDatabase).findById(resourceGroupId);
        return upcastResourceGroups(resourceGroups);
    }

    /**
     * Updates an existing resource group with new values.
     *
     * @return the updated ResourceGroupDetail object
     */
    @Override
    public ResourceGroupsDetail updateResourceGroup(ResourceGroupsDetail resourceGroup,
            @Nullable String routingGroupDatabase)
    {
        ResourceGroupsDao dao = getResourceGroupsDao(routingGroupDatabase);
        ResourceGroups model = dao.findFirstById(resourceGroup.getResourceGroupId());
        if (model == null) {
            dao.create(resourceGroup);
        }
        else {
            dao.update(resourceGroup);
        }
        return resourceGroup;
    }

    /**
     * Search for resource group by its resourceGroupId and delete it.
     */
    @Override
    public void deleteResourceGroup(long resourceGroupId, @Nullable String routingGroupDatabase)
    {
        getResourceGroupsDao(routingGroupDatabase).deleteById(resourceGroupId);
    }

    /**
     * Creates and returns a selector with the given parameters.
     *
     * @return selector
     */
    @Override
    public SelectorsDetail createSelector(SelectorsDetail selector,
            @Nullable String routingGroupDatabase)
    {
        getSelectorsDao(routingGroupDatabase).insert(selector);
        return selector;
    }

    /**
     * Retrieves a list of all existing resource groups.
     *
     * @return all existing selectors as a list of SelectorDetail objects
     */
    @Override
    public List<SelectorsDetail> readAllSelectors(@Nullable String routingGroupDatabase)
    {
        List<Selectors> selectorList = getSelectorsDao(routingGroupDatabase).findAll();
        return upcastSelectors(selectorList);
    }

    /**
     * Retrieves the selector.
     */
    @Override
    public List<SelectorsDetail> readSelector(long resourceGroupId,
            @Nullable String routingGroupDatabase)
    {
        List<Selectors> selectorList = getSelectorsDao(routingGroupDatabase).findByResourceGroupId(resourceGroupId);
        return upcastSelectors(selectorList);
    }

    /**
     * Updates a selector given the specified selector and its updated version.
     */
    @Override
    public SelectorsDetail updateSelector(SelectorsDetail selector, SelectorsDetail updatedSelector,
            @Nullable String routingGroupDatabase)
    {
        SelectorsDao dao = getSelectorsDao(routingGroupDatabase);
        Selectors model = dao.findFirst(selector);
        if (model == null) {
            dao.insert(updatedSelector);
        }
        else {
            dao.update(selector, updatedSelector);
        }
        return updatedSelector;
    }

    /**
     * Search for selector by its exact properties and delete it.
     */
    @Override
    public void deleteSelector(SelectorsDetail selector, @Nullable String routingGroupDatabase)
    {
        getSelectorsDao(routingGroupDatabase).delete(selector);
    }

    /**
     * Create new global property with given parameters.
     */
    @Override
    public GlobalPropertiesDetail createGlobalProperty(GlobalPropertiesDetail globalPropertyDetail,
            @Nullable String routingGroupDatabase)
    {
        getDao(routingGroupDatabase).insert(globalPropertyDetail.getName(), globalPropertyDetail.getValue());
        return globalPropertyDetail;
    }

    /**
     * Read all existing global properties.
     */
    @Override
    public List<GlobalPropertiesDetail> readAllGlobalProperties(
            @Nullable String routingGroupDatabase)
    {
        List<ResourceGroupsGlobalProperties> globalPropertyList = getDao(routingGroupDatabase).findAll();
        return upcast(globalPropertyList);
    }

    /**
     * Read specific global property based on the given name.
     */
    @Override
    public List<GlobalPropertiesDetail> readGlobalProperty(String name,
            @Nullable String routingGroupDatabase)
    {
        List<ResourceGroupsGlobalProperties> globalPropertyList = getDao(routingGroupDatabase).findByName(name);
        return upcast(globalPropertyList);
    }

    /**
     * Updates a global property based on the given name.
     */
    @Override
    public GlobalPropertiesDetail updateGlobalProperty(GlobalPropertiesDetail globalProperty,
            @Nullable String routingGroupDatabase)
    {
        ResourceGroupsGlobalPropertiesDao dao = getDao(routingGroupDatabase);
        ResourceGroupsGlobalProperties model = dao.findFirstByName(globalProperty.getName());

        if (model == null) {
            dao.insert(globalProperty.getName(), globalProperty.getValue());
        }
        else {
            dao.update(globalProperty.getName(), globalProperty.getValue());
        }
        return globalProperty;
    }

    /**
     * Deletes a global property from the table based on its name.
     */
    @Override
    public void deleteGlobalProperty(String name, @Nullable String routingGroupDatabase)
    {
        getDao(routingGroupDatabase).deleteByName(name);
    }

    /**
     * Creates exact match source selector for db.
     */
    @Override
    public ExactSelectorsDetail createExactMatchSourceSelector(
            ExactSelectorsDetail exactSelectorDetail)
    {
        exactMatchSourceSelectorsDao.insert(exactSelectorDetail);
        return exactSelectorDetail;
    }

    /**
     * Reads exact match source selector from db.
     */
    @Override
    public List<ExactSelectorsDetail> readExactMatchSourceSelector()
    {
        List<ExactMatchSourceSelectors> exactMatchSourceSelectors = exactMatchSourceSelectorsDao.findAll();
        return exactMatchSourceSelectors.stream()
                .map(HaResourceGroupsManager::upcastExactSelectors)
                .collect(toImmutableList());
    }

    private SelectorsDao getSelectorsDao(@Nullable String routingGroupDatabase)
    {
        return connectionManager.getJdbi(routingGroupDatabase).onDemand(SelectorsDao.class);
    }

    private ResourceGroupsDao getResourceGroupsDao(@Nullable String routingGroupDatabase)
    {
        return connectionManager.getJdbi(routingGroupDatabase).onDemand(ResourceGroupsDao.class);
    }

    private ResourceGroupsGlobalPropertiesDao getDao(@Nullable String routingGroupDatabase)
    {
        return connectionManager.getJdbi(routingGroupDatabase).onDemand(ResourceGroupsGlobalPropertiesDao.class);
    }

    private static List<GlobalPropertiesDetail> upcast(List<ResourceGroupsGlobalProperties> globalPropertiesList)
    {
        List<GlobalPropertiesDetail> globalProperties = new ArrayList<>();
        for (ResourceGroupsGlobalProperties dao : globalPropertiesList) {
            GlobalPropertiesDetail globalPropertiesDetail = new GlobalPropertiesDetail();
            globalPropertiesDetail.setName(dao.name());
            globalPropertiesDetail.setValue(dao.value());

            globalProperties.add(globalPropertiesDetail);
        }
        return globalProperties;
    }

    private static ExactSelectorsDetail upcastExactSelectors(ExactMatchSourceSelectors exactMatchSourceSelector)
    {
        ExactSelectorsDetail exactSelectorDetail = new ExactSelectorsDetail();
        exactSelectorDetail.setResourceGroupId(exactMatchSourceSelector.resourceGroupId());
        exactSelectorDetail.setUpdateTime(exactMatchSourceSelector.updateTime());
        exactSelectorDetail.setSource(exactMatchSourceSelector.source());
        exactSelectorDetail.setEnvironment(exactMatchSourceSelector.environment());
        exactSelectorDetail.setQueryType(exactMatchSourceSelector.queryType());
        return exactSelectorDetail;
    }

    private static List<SelectorsDetail> upcastSelectors(List<Selectors> selectorList)
    {
        ImmutableList.Builder<SelectorsDetail> builder = ImmutableList.builder();
        for (Selectors selectors : selectorList) {
            SelectorsDetail selectorDetail = new SelectorsDetail();
            selectorDetail.setResourceGroupId(selectors.resourceGroupId());
            selectorDetail.setPriority(selectors.priority());
            selectorDetail.setUserRegex(selectors.userRegex());
            selectorDetail.setSourceRegex(selectors.sourceRegex());
            selectorDetail.setQueryType(selectors.queryType());
            selectorDetail.setClientTags(selectors.clientTags());
            selectorDetail.setSelectorResourceEstimate(selectors.selectorResourceEstimate());
            builder.add(selectorDetail);
        }
        return builder.build();
    }

    private static List<ResourceGroupsDetail> upcastResourceGroups(List<ResourceGroups> resourceGroupList)
    {
        ImmutableList.Builder<ResourceGroupsDetail> builder = new ImmutableList.Builder<>();
        for (ResourceGroups resourceGroups : resourceGroupList) {
            ResourceGroupsDetail resourceGroupDetail = new ResourceGroupsDetail();
            resourceGroupDetail.setResourceGroupId(resourceGroups.resourceGroupId());
            resourceGroupDetail.setName(resourceGroups.name());

            resourceGroupDetail.setParent(resourceGroups.parent());
            resourceGroupDetail.setJmxExport(resourceGroups.jmxExport());
            resourceGroupDetail.setSchedulingPolicy(resourceGroups.schedulingPolicy());
            resourceGroupDetail.setSchedulingWeight(resourceGroups.schedulingWeight());

            resourceGroupDetail.setSoftMemoryLimit(resourceGroups.softMemoryLimit());
            resourceGroupDetail.setMaxQueued(resourceGroups.maxQueued());
            resourceGroupDetail.setHardConcurrencyLimit(resourceGroups.hardConcurrencyLimit());

            resourceGroupDetail.setSoftConcurrencyLimit(resourceGroups.softConcurrencyLimit());
            resourceGroupDetail.setSoftCpuLimit(resourceGroups.softCpuLimit());
            resourceGroupDetail.setHardCpuLimit(resourceGroups.hardCpuLimit());
            resourceGroupDetail.setEnvironment(resourceGroups.environment());

            builder.add(resourceGroupDetail);
        }
        return builder.build();
    }
}
