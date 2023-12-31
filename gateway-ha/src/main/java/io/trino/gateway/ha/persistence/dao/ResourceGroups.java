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
package io.trino.gateway.ha.persistence.dao;

import org.javalite.activejdbc.Model;
import org.javalite.activejdbc.annotations.BelongsTo;
import org.javalite.activejdbc.annotations.Cached;
import org.javalite.activejdbc.annotations.HasMany;
import org.javalite.activejdbc.annotations.IdName;
import org.javalite.activejdbc.annotations.Table;

import java.util.ArrayList;
import java.util.List;

import static io.trino.gateway.ha.router.ResourceGroupsManager.ResourceGroupsDetail;

@BelongsTo(parent = ResourceGroups.class, foreignKeyName = "parent")
@HasMany(child = ResourceGroups.class, foreignKeyName = "parent")
@IdName("resource_group_id")
@Table("resource_groups") // located in gateway-ha-persistence.sql
@Cached
public class ResourceGroups
        extends Model
{
    private static final String resourceGroupId = "resource_group_id";
    private static final String name = "name";

    /* OPTIONAL POLICY CONTROLS */
    private static final String parent = "parent";
    private static final String jmxExport = "jmx_export";
    private static final String schedulingPolicy = "scheduling_policy";
    private static final String schedulingWeight = "scheduling_weight";

    /* REQUIRED QUOTAS */
    private static final String softMemoryLimit = "soft_memory_limit";
    private static final String maxQueued = "max_queued";
    private static final String hardConcurrencyLimit = "hard_concurrency_limit";

    /* OPTIONAL QUOTAS */
    private static final String softConcurrencyLimit = "soft_concurrency_limit";
    private static final String softCpuLimit = "soft_cpu_limit";
    private static final String hardCpuLimit = "hard_cpu_limit";
    private static final String environment = "environment";

    /**
     * Reads all existing resource groups and returns them in a list.
     *
     * @return List of ResourceGroupDetail objects
     */
    public static List<ResourceGroupsDetail> upcast(List<ResourceGroups> resourceGroupList)
    {
        List<ResourceGroupsDetail> resourceGroupDetails = new ArrayList<>();
        for (ResourceGroups dao : resourceGroupList) {
            ResourceGroupsDetail resourceGroupDetail = new ResourceGroupsDetail();
            resourceGroupDetail.setResourceGroupId(dao.getLong(resourceGroupId));
            resourceGroupDetail.setName(dao.getString(name));

            resourceGroupDetail.setParent(dao.getLong(parent));
            resourceGroupDetail.setJmxExport(dao.getBoolean(jmxExport));
            resourceGroupDetail.setSchedulingPolicy(dao.getString(schedulingPolicy));
            resourceGroupDetail.setSchedulingWeight(dao.getInteger(schedulingWeight));

            resourceGroupDetail.setSoftMemoryLimit(dao.getString(softMemoryLimit));
            resourceGroupDetail.setMaxQueued(dao.getInteger(maxQueued));
            resourceGroupDetail.setHardConcurrencyLimit(dao.getInteger(hardConcurrencyLimit));

            resourceGroupDetail.setSoftConcurrencyLimit(dao.getInteger(softConcurrencyLimit));
            resourceGroupDetail.setSoftCpuLimit(dao.getString(softCpuLimit));
            resourceGroupDetail.setHardCpuLimit(dao.getString(hardCpuLimit));
            resourceGroupDetail.setEnvironment(dao.getString(environment));

            resourceGroupDetails.add(resourceGroupDetail);
        }
        return resourceGroupDetails;
    }

    /**
     * Creates a new ResourceGroup model.
     */
    public static void create(ResourceGroups model, ResourceGroupsDetail resourceGroupDetail)
    {
        model.set(resourceGroupId, resourceGroupDetail.getResourceGroupId());
        model.set(name, resourceGroupDetail.getName());

        model.set(parent, resourceGroupDetail.getParent());
        model.set(jmxExport, resourceGroupDetail.getJmxExport());
        model.set(schedulingPolicy, resourceGroupDetail.getSchedulingPolicy());
        model.set(schedulingWeight, resourceGroupDetail.getSchedulingWeight());

        model.set(softMemoryLimit, resourceGroupDetail.getSoftMemoryLimit());
        model.set(maxQueued, resourceGroupDetail.getMaxQueued());
        model.set(hardConcurrencyLimit, resourceGroupDetail.getHardConcurrencyLimit());

        model.set(softConcurrencyLimit, resourceGroupDetail.getSoftConcurrencyLimit());
        model.set(softCpuLimit, resourceGroupDetail.getSoftCpuLimit());
        model.set(hardCpuLimit, resourceGroupDetail.getHardCpuLimit());
        model.set(environment, resourceGroupDetail.getEnvironment());

        model.insert();
    }

    /**
     * Updates and saves an existing ResourceGroup model.
     */
    public static void update(ResourceGroups model, ResourceGroupsDetail resourceGroupDetail)
    {
        model
                .set(resourceGroupId, resourceGroupDetail.getResourceGroupId())
                .set(name, resourceGroupDetail.getName())
                .set(parent, resourceGroupDetail.getParent())
                .set(jmxExport, resourceGroupDetail.getJmxExport())
                .set(schedulingPolicy, resourceGroupDetail.getSchedulingPolicy())
                .set(schedulingWeight, resourceGroupDetail.getSchedulingWeight())
                .set(softMemoryLimit, resourceGroupDetail.getSoftMemoryLimit())
                .set(maxQueued, resourceGroupDetail.getMaxQueued())
                .set(hardConcurrencyLimit, resourceGroupDetail.getHardConcurrencyLimit())
                .set(softConcurrencyLimit, resourceGroupDetail.getSoftConcurrencyLimit())
                .set(softCpuLimit, resourceGroupDetail.getSoftCpuLimit())
                .set(hardCpuLimit, resourceGroupDetail.getHardCpuLimit())
                .set(environment, resourceGroupDetail.getEnvironment())
                .saveIt();
    }
}
