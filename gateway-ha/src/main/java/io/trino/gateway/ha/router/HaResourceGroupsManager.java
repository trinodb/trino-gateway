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
import io.trino.gateway.ha.persistence.dao.ExactMatchSourceSelectors;
import io.trino.gateway.ha.persistence.dao.ExactMatchSourceSelectorsDao;
import io.trino.gateway.ha.persistence.dao.ResourceGroups;
import io.trino.gateway.ha.persistence.dao.ResourceGroupsGlobalProperties;
import io.trino.gateway.ha.persistence.dao.ResourceGroupsGlobalPropertiesDao;
import io.trino.gateway.ha.persistence.dao.Selectors;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.lang.String.format;

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
        try {
            connectionManager.open(routingGroupDatabase);
            ResourceGroups.create(new ResourceGroups(), resourceGroup);
        }
        finally {
            connectionManager.close();
        }
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
        try {
            connectionManager.open(routingGroupDatabase);
            List<ResourceGroups> resourceGroupList = ResourceGroups.findAll();
            return ResourceGroups.upcast(resourceGroupList);
        }
        finally {
            connectionManager.close();
        }
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
        try {
            connectionManager.open(routingGroupDatabase);
            List<ResourceGroups> resourceGroup =
                    ResourceGroups.where("resource_group_id = ?", resourceGroupId);
            return ResourceGroups.upcast(resourceGroup);
        }
        finally {
            connectionManager.close();
        }
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
        try {
            connectionManager.open(routingGroupDatabase);
            ResourceGroups model =
                    ResourceGroups.findFirst("resource_group_id = ?",
                            resourceGroup.getResourceGroupId());

            if (model == null) {
                ResourceGroups.create(new ResourceGroups(), resourceGroup);
            }
            else {
                ResourceGroups.update(model, resourceGroup);
            }
        }
        finally {
            connectionManager.close();
        }
        return resourceGroup;
    }

    /**
     * Search for resource group by its resourceGroupId and delete it.
     */
    @Override
    public void deleteResourceGroup(long resourceGroupId, @Nullable String routingGroupDatabase)
    {
        try {
            connectionManager.open(routingGroupDatabase);
            ResourceGroups.delete("resource_group_id = ?", resourceGroupId);
        }
        finally {
            connectionManager.close();
        }
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
        try {
            connectionManager.open(routingGroupDatabase);
            Selectors.create(new Selectors(), selector);
        }
        finally {
            connectionManager.close();
        }
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
        try {
            connectionManager.open(routingGroupDatabase);
            List<Selectors> selectorList = Selectors.findAll();
            return Selectors.upcast(selectorList);
        }
        finally {
            connectionManager.close();
        }
    }

    /**
     * Retrieves the selector.
     */
    @Override
    public List<SelectorsDetail> readSelector(long resourceGroupId,
            @Nullable String routingGroupDatabase)
    {
        try {
            connectionManager.open(routingGroupDatabase);
            List<Selectors> selectorList = Selectors.where("resource_group_id = ?",
                    resourceGroupId);
            return Selectors.upcast(selectorList);
        }
        finally {
            connectionManager.close();
        }
    }

    /**
     * Updates a selector given the specified selector and its updated version.
     */
    @Override
    public SelectorsDetail updateSelector(SelectorsDetail selector, SelectorsDetail updatedSelector,
            @Nullable String routingGroupDatabase)
    {
        try {
            connectionManager.open(routingGroupDatabase);
            String query =
                    format(
                            "resource_group_id %s and priority %s "
                                    + "and user_regex %s and source_regex %s "
                                    + "and query_type %s and client_tags %s "
                                    + "and selector_resource_estimate %s",
                            getMatchingString(selector.getResourceGroupId()),
                            getMatchingString(selector.getPriority()),
                            getMatchingString(selector.getUserRegex()),
                            getMatchingString(selector.getSourceRegex()),
                            getMatchingString(selector.getQueryType()),
                            getMatchingString(selector.getClientTags()),
                            getMatchingString(selector.getSelectorResourceEstimate()));
            Selectors model = Selectors.findFirst(query);

            if (model == null) {
                Selectors.create(new Selectors(), updatedSelector);
            }
            else {
                Selectors.update(model, updatedSelector);
            }
        }
        finally {
            connectionManager.close();
        }
        return updatedSelector;
    }

    /**
     * Search for selector by its exact properties and delete it.
     */
    @Override
    public void deleteSelector(SelectorsDetail selector, @Nullable String routingGroupDatabase)
    {
        try {
            connectionManager.open(routingGroupDatabase);
            String query =
                    format(
                            "resource_group_id %s and priority %s "
                                    + "and user_regex %s and source_regex %s "
                                    + "and query_type %s and client_tags %s "
                                    + "and selector_resource_estimate %s",
                            getMatchingString(selector.getResourceGroupId()),
                            getMatchingString(selector.getPriority()),
                            getMatchingString(selector.getUserRegex()),
                            getMatchingString(selector.getSourceRegex()),
                            getMatchingString(selector.getQueryType()),
                            getMatchingString(selector.getClientTags()),
                            getMatchingString(selector.getSelectorResourceEstimate()));
            Selectors.delete(query);
        }
        finally {
            connectionManager.close();
        }
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

    /**
     * Gets exact match source selector from db.
     */
    @Override
    public ExactSelectorsDetail getExactMatchSourceSelector(
            ExactSelectorsDetail exactSelectorDetail)
    {
        ExactMatchSourceSelectors exactSelector = exactMatchSourceSelectorsDao.findFirst(exactSelectorDetail);
        return upcastExactSelectors(exactSelector);
    }

    public String getMatchingString(Object detail)
    {
        if (detail == null) {
            return "IS NULL";
        }
        else if (detail.getClass().equals(String.class)) {
            return "= '" + detail + "'";
        }
        return "= " + detail;
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
}
