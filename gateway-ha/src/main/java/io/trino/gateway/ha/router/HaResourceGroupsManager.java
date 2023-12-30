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
import io.trino.gateway.ha.persistence.dao.ResourceGroups;
import io.trino.gateway.ha.persistence.dao.ResourceGroupsGlobalProperties;
import io.trino.gateway.ha.persistence.dao.Selectors;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public class HaResourceGroupsManager
        implements ResourceGroupsManager
{
    private final JdbcConnectionManager connectionManager;

    public HaResourceGroupsManager(JdbcConnectionManager connectionManager)
    {
        this.connectionManager = connectionManager;
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
                    String.format(
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
                    String.format(
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
        try {
            connectionManager.open(routingGroupDatabase);
            ResourceGroupsGlobalProperties.create(
                    new ResourceGroupsGlobalProperties(), globalPropertyDetail);
        }
        finally {
            connectionManager.close();
        }
        return globalPropertyDetail;
    }

    /**
     * Read all existing global properties.
     */
    @Override
    public List<GlobalPropertiesDetail> readAllGlobalProperties(
            @Nullable String routingGroupDatabase)
    {
        try {
            connectionManager.open(routingGroupDatabase);
            List<ResourceGroupsGlobalProperties> globalPropertyList =
                    ResourceGroupsGlobalProperties.findAll();
            return ResourceGroupsGlobalProperties.upcast(globalPropertyList);
        }
        finally {
            connectionManager.close();
        }
    }

    /**
     * Read specific global property based on the given name.
     */
    @Override
    public List<GlobalPropertiesDetail> readGlobalProperty(String name,
            @Nullable String routingGroupDatabase)
    {
        try {
            connectionManager.open(routingGroupDatabase);
            List<ResourceGroupsGlobalProperties> globalPropertyList =
                    ResourceGroupsGlobalProperties.where("name = ?", name);
            return ResourceGroupsGlobalProperties.upcast(globalPropertyList);
        }
        finally {
            connectionManager.close();
        }
    }

    /**
     * Updates a global property based on the given name.
     */
    @Override
    public GlobalPropertiesDetail updateGlobalProperty(GlobalPropertiesDetail globalProperty,
            @Nullable String routingGroupDatabase)
    {
        try {
            connectionManager.open(routingGroupDatabase);
            ResourceGroupsGlobalProperties model =
                    ResourceGroupsGlobalProperties.findFirst("name = ?", globalProperty.getName());

            if (model == null) {
                ResourceGroupsGlobalProperties.create(new ResourceGroupsGlobalProperties(), globalProperty);
            }
            else {
                ResourceGroupsGlobalProperties.update(model, globalProperty);
            }
        }
        finally {
            connectionManager.close();
        }
        return globalProperty;
    }

    /**
     * Deletes a global property from the table based on its name.
     */
    @Override
    public void deleteGlobalProperty(String name, @Nullable String routingGroupDatabase)
    {
        try {
            connectionManager.open(routingGroupDatabase);
            ResourceGroupsGlobalProperties.delete("name = ?", name);
        }
        finally {
            connectionManager.close();
        }
    }

    /**
     * Creates exact match source selector for db.
     */
    @Override
    public ExactSelectorsDetail createExactMatchSourceSelector(
            ExactSelectorsDetail exactSelectorDetail)
    {
        try {
            connectionManager.open();
            ExactMatchSourceSelectors.create(new ExactMatchSourceSelectors(), exactSelectorDetail);
        }
        finally {
            connectionManager.close();
        }
        return exactSelectorDetail;
    }

    /**
     * Reads exact match source selector from db.
     */
    @Override
    public List<ExactSelectorsDetail> readExactMatchSourceSelector()
    {
        try {
            connectionManager.open();
            List<ExactMatchSourceSelectors> exactMatchSourceSelectorList =
                    ExactMatchSourceSelectors.findAll();
            return ExactMatchSourceSelectors.upcast(exactMatchSourceSelectorList);
        }
        finally {
            connectionManager.close();
        }
    }

    /**
     * Gets exact match source selector from db.
     */
    @Override
    public ExactSelectorsDetail getExactMatchSourceSelector(
            ExactSelectorsDetail exactSelectorDetail)
    {
        try {
            connectionManager.open();
            ExactMatchSourceSelectors model =
                    ExactMatchSourceSelectors.findFirst(
                            "resource_group_id = ? and update_time = ? "
                                    + "and source = ? and environment = ? and query_type = ?",
                            exactSelectorDetail.getResourceGroupId(),
                            exactSelectorDetail.getUpdateTime(),
                            exactSelectorDetail.getSource(),
                            exactSelectorDetail.getEnvironment(),
                            exactSelectorDetail.getQueryType());

            List<ExactMatchSourceSelectors> exactMatchSourceSelectorList = new ArrayList();
            exactMatchSourceSelectorList.add(model);

            if (model == null) {
                return null;
            }
            else {
                ExactMatchSourceSelectors.upcast(exactMatchSourceSelectorList);
            }
        }
        finally {
            connectionManager.close();
        }
        return exactSelectorDetail;
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
}
