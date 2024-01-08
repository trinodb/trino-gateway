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

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.List;
import java.util.Objects;

import static com.google.common.base.MoreObjects.toStringHelper;

public interface ResourceGroupsManager
{
    ResourceGroupsDetail createResourceGroup(ResourceGroupsDetail resourceGroup,
            @Nullable String routingGroupDatabase);

    List<ResourceGroupsDetail> readAllResourceGroups(@Nullable String routingGroupDatabase);

    List<ResourceGroupsDetail> readResourceGroup(long resourceGroupId,
            @Nullable String routingGroupDatabase);

    ResourceGroupsDetail updateResourceGroup(ResourceGroupsDetail resourceGroup,
            @Nullable String routingGroupDatabase);

    void deleteResourceGroup(long resourceGroupId, @Nullable String routingGroupDatabase);

    SelectorsDetail createSelector(SelectorsDetail selector, @Nullable String routingGroupDatabase);

    List<SelectorsDetail> readAllSelectors(@Nullable String routingGroupDatabase);

    List<SelectorsDetail> readSelector(long resourceGroupId, @Nullable String routingGrouoDatabase);

    SelectorsDetail updateSelector(SelectorsDetail selector, SelectorsDetail updatedSelector,
            @Nullable String routingGroupDatabase);

    void deleteSelector(SelectorsDetail selector, @Nullable String routingGroupDatabase);

    GlobalPropertiesDetail createGlobalProperty(GlobalPropertiesDetail globalPropertyDetail,
            @Nullable String routingGroupDatabase);

    List<GlobalPropertiesDetail> readAllGlobalProperties(@Nullable String routingGroupDatabase);

    List<GlobalPropertiesDetail> readGlobalProperty(String name,
            @Nullable String routingGroupDatabase);

    GlobalPropertiesDetail updateGlobalProperty(GlobalPropertiesDetail globalProperty,
            @Nullable String routingGroupDatabase);

    void deleteGlobalProperty(String name, @Nullable String routingGroupDatabase);

    ExactSelectorsDetail createExactMatchSourceSelector(ExactSelectorsDetail exactSelectorDetail);

    List<ExactSelectorsDetail> readExactMatchSourceSelector();

    ExactSelectorsDetail getExactMatchSourceSelector(ExactSelectorsDetail exactSelectorDetail);

    class ResourceGroupsDetail
            implements Comparable<ResourceGroupsDetail>
    {
        @Nonnull private long resourceGroupId;
        @Nonnull private String name;

        /* OPTIONAL POLICY CONTROLS */
        private Long parent;
        private Boolean jmxExport;
        private String schedulingPolicy;
        private Integer schedulingWeight;

        /* REQUIRED QUOTAS */
        @Nonnull private String softMemoryLimit;
        @Nonnull private int maxQueued;
        @Nonnull private int hardConcurrencyLimit;

        /* OPTIONAL QUOTAS */
        private Integer softConcurrencyLimit;
        private String softCpuLimit;
        private String hardCpuLimit;
        private String environment;

        public ResourceGroupsDetail() {}

        public ResourceGroupsDetail(@Nonnull long resourceGroupId, @Nonnull String name, @Nonnull String softMemoryLimit, @Nonnull int maxQueued, @Nonnull int hardConcurrencyLimit)
        {
            this.resourceGroupId = resourceGroupId;
            this.name = name;
            this.softMemoryLimit = softMemoryLimit;
            this.maxQueued = maxQueued;
            this.hardConcurrencyLimit = hardConcurrencyLimit;
        }

        @Override
        public int compareTo(ResourceGroupsDetail o)
        {
            if (this.resourceGroupId < o.resourceGroupId) {
                return 1;
            }
            else {
                return this.resourceGroupId == o.resourceGroupId ? 0 : -1;
            }
        }

        public @Nonnull long getResourceGroupId()
        {
            return this.resourceGroupId;
        }

        public void setResourceGroupId(@Nonnull long resourceGroupId)
        {
            this.resourceGroupId = resourceGroupId;
        }

        public @Nonnull String getName()
        {
            return this.name;
        }

        public void setName(@Nonnull String name)
        {
            this.name = name;
        }

        public Long getParent()
        {
            return this.parent;
        }

        public void setParent(Long parent)
        {
            this.parent = parent;
        }

        public Boolean getJmxExport()
        {
            return this.jmxExport;
        }

        public void setJmxExport(Boolean jmxExport)
        {
            this.jmxExport = jmxExport;
        }

        public String getSchedulingPolicy()
        {
            return this.schedulingPolicy;
        }

        public void setSchedulingPolicy(String schedulingPolicy)
        {
            this.schedulingPolicy = schedulingPolicy;
        }

        public Integer getSchedulingWeight()
        {
            return this.schedulingWeight;
        }

        public void setSchedulingWeight(Integer schedulingWeight)
        {
            this.schedulingWeight = schedulingWeight;
        }

        public @Nonnull String getSoftMemoryLimit()
        {
            return this.softMemoryLimit;
        }

        public void setSoftMemoryLimit(@Nonnull String softMemoryLimit)
        {
            this.softMemoryLimit = softMemoryLimit;
        }

        public @Nonnull int getMaxQueued()
        {
            return this.maxQueued;
        }

        public void setMaxQueued(@Nonnull int maxQueued)
        {
            this.maxQueued = maxQueued;
        }

        public @Nonnull int getHardConcurrencyLimit()
        {
            return this.hardConcurrencyLimit;
        }

        public void setHardConcurrencyLimit(@Nonnull int hardConcurrencyLimit)
        {
            this.hardConcurrencyLimit = hardConcurrencyLimit;
        }

        public Integer getSoftConcurrencyLimit()
        {
            return this.softConcurrencyLimit;
        }

        public void setSoftConcurrencyLimit(Integer softConcurrencyLimit)
        {
            this.softConcurrencyLimit = softConcurrencyLimit;
        }

        public String getSoftCpuLimit()
        {
            return this.softCpuLimit;
        }

        public void setSoftCpuLimit(String softCpuLimit)
        {
            this.softCpuLimit = softCpuLimit;
        }

        public String getHardCpuLimit()
        {
            return this.hardCpuLimit;
        }

        public void setHardCpuLimit(String hardCpuLimit)
        {
            this.hardCpuLimit = hardCpuLimit;
        }

        public String getEnvironment()
        {
            return this.environment;
        }

        public void setEnvironment(String environment)
        {
            this.environment = environment;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ResourceGroupsDetail that = (ResourceGroupsDetail) o;
            return resourceGroupId == that.resourceGroupId &&
                    maxQueued == that.maxQueued &&
                    hardConcurrencyLimit == that.hardConcurrencyLimit &&
                    Objects.equals(name, that.name) &&
                    Objects.equals(parent, that.parent) &&
                    Objects.equals(jmxExport, that.jmxExport) &&
                    Objects.equals(schedulingPolicy, that.schedulingPolicy) &&
                    Objects.equals(schedulingWeight, that.schedulingWeight) &&
                    Objects.equals(softMemoryLimit, that.softMemoryLimit) &&
                    Objects.equals(softConcurrencyLimit, that.softConcurrencyLimit) &&
                    Objects.equals(softCpuLimit, that.softCpuLimit) &&
                    Objects.equals(hardCpuLimit, that.hardCpuLimit) &&
                    Objects.equals(environment, that.environment);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(resourceGroupId, name, parent, jmxExport, schedulingPolicy, schedulingWeight, softMemoryLimit, maxQueued, hardConcurrencyLimit, softConcurrencyLimit, softCpuLimit, hardCpuLimit, environment);
        }

        @Override
        public String toString()
        {
            return toStringHelper(this)
                    .add("resourceGroupId", resourceGroupId)
                    .add("name", name)
                    .add("parent", parent)
                    .add("jmxExport", jmxExport)
                    .add("schedulingPolicy", schedulingPolicy)
                    .add("schedulingWeight", schedulingWeight)
                    .add("softMemoryLimit", softMemoryLimit)
                    .add("maxQueued", maxQueued)
                    .add("hardConcurrencyLimit", hardConcurrencyLimit)
                    .add("softConcurrencyLimit", softConcurrencyLimit)
                    .add("softCpuLimit", softCpuLimit)
                    .add("hardCpuLimit", hardCpuLimit)
                    .add("environment", environment)
                    .toString();
        }
    }

    class SelectorsDetail
            implements Comparable<SelectorsDetail>
    {
        @Nonnull private long resourceGroupId;
        @Nonnull private long priority;

        private String userRegex;
        private String sourceRegex;

        private String queryType;
        private String clientTags;
        private String selectorResourceEstimate;

        public SelectorsDetail() {}

        public SelectorsDetail(@Nonnull long resourceGroupId, @Nonnull long priority)
        {
            this.resourceGroupId = resourceGroupId;
            this.priority = priority;
        }

        @Override
        public int compareTo(SelectorsDetail o)
        {
            if (this.resourceGroupId < o.resourceGroupId) {
                return 1;
            }
            else {
                return this.resourceGroupId == o.resourceGroupId ? 0 : -1;
            }
        }

        public @Nonnull long getResourceGroupId()
        {
            return this.resourceGroupId;
        }

        public void setResourceGroupId(@Nonnull long resourceGroupId)
        {
            this.resourceGroupId = resourceGroupId;
        }

        public @Nonnull long getPriority()
        {
            return this.priority;
        }

        public void setPriority(@Nonnull long priority)
        {
            this.priority = priority;
        }

        public String getUserRegex()
        {
            return this.userRegex;
        }

        public void setUserRegex(String userRegex)
        {
            this.userRegex = userRegex;
        }

        public String getSourceRegex()
        {
            return this.sourceRegex;
        }

        public void setSourceRegex(String sourceRegex)
        {
            this.sourceRegex = sourceRegex;
        }

        public String getQueryType()
        {
            return this.queryType;
        }

        public void setQueryType(String queryType)
        {
            this.queryType = queryType;
        }

        public String getClientTags()
        {
            return this.clientTags;
        }

        public void setClientTags(String clientTags)
        {
            this.clientTags = clientTags;
        }

        public String getSelectorResourceEstimate()
        {
            return this.selectorResourceEstimate;
        }

        public void setSelectorResourceEstimate(String selectorResourceEstimate)
        {
            this.selectorResourceEstimate = selectorResourceEstimate;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            SelectorsDetail that = (SelectorsDetail) o;
            return resourceGroupId == that.resourceGroupId &&
                    priority == that.priority &&
                    Objects.equals(userRegex, that.userRegex) &&
                    Objects.equals(sourceRegex, that.sourceRegex) &&
                    Objects.equals(queryType, that.queryType) &&
                    Objects.equals(clientTags, that.clientTags) &&
                    Objects.equals(selectorResourceEstimate, that.selectorResourceEstimate);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(resourceGroupId, priority, userRegex, sourceRegex, queryType, clientTags, selectorResourceEstimate);
        }

        @Override
        public String toString()
        {
            return toStringHelper(this)
                    .add("resourceGroupId", resourceGroupId)
                    .add("priority", priority)
                    .add("userRegex", userRegex)
                    .add("sourceRegex", sourceRegex)
                    .add("queryType", queryType)
                    .add("clientTags", clientTags)
                    .add("selectorResourceEstimate", selectorResourceEstimate)
                    .toString();
        }
    }

    class GlobalPropertiesDetail
            implements Comparable<GlobalPropertiesDetail>
    {
        @Nonnull private String name;
        private String value;

        public GlobalPropertiesDetail() {}

        public GlobalPropertiesDetail(@Nonnull String name)
        {
            this.name = name;
        }

        @Override
        public int compareTo(GlobalPropertiesDetail o)
        {
            return 0;
        }

        public @Nonnull String getName()
        {
            return this.name;
        }

        public void setName(@Nonnull String name)
        {
            this.name = name;
        }

        public String getValue()
        {
            return this.value;
        }

        public void setValue(String value)
        {
            this.value = value;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            GlobalPropertiesDetail that = (GlobalPropertiesDetail) o;
            return Objects.equals(name, that.name) && Objects.equals(value, that.value);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(name, value);
        }

        @Override
        public String toString()
        {
            return toStringHelper(this)
                    .add("name", name)
                    .add("value", value)
                    .toString();
        }
    }

    class ExactSelectorsDetail
            implements Comparable<ExactSelectorsDetail>
    {
        @Nonnull private String resourceGroupId;
        @Nonnull private String updateTime;

        @Nonnull private String source;
        private String environment;
        private String queryType;

        public ExactSelectorsDetail() {}

        public ExactSelectorsDetail(@Nonnull String resourceGroupId, @Nonnull String updateTime, @Nonnull String source)
        {
            this.resourceGroupId = resourceGroupId;
            this.updateTime = updateTime;
            this.source = source;
        }

        @Override
        public int compareTo(ExactSelectorsDetail o)
        {
            return 0;
        }

        public @Nonnull String getResourceGroupId()
        {
            return this.resourceGroupId;
        }

        public void setResourceGroupId(@Nonnull String resourceGroupId)
        {
            this.resourceGroupId = resourceGroupId;
        }

        public @Nonnull String getUpdateTime()
        {
            return this.updateTime;
        }

        public void setUpdateTime(@Nonnull String updateTime)
        {
            this.updateTime = updateTime;
        }

        public @Nonnull String getSource()
        {
            return this.source;
        }

        public void setSource(@Nonnull String source)
        {
            this.source = source;
        }

        public String getEnvironment()
        {
            return this.environment;
        }

        public void setEnvironment(String environment)
        {
            this.environment = environment;
        }

        public String getQueryType()
        {
            return this.queryType;
        }

        public void setQueryType(String queryType)
        {
            this.queryType = queryType;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ExactSelectorsDetail that = (ExactSelectorsDetail) o;
            return Objects.equals(resourceGroupId, that.resourceGroupId) &&
                    Objects.equals(updateTime, that.updateTime) &&
                    Objects.equals(source, that.source) &&
                    Objects.equals(environment, that.environment) &&
                    Objects.equals(queryType, that.queryType);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(resourceGroupId, updateTime, source, environment, queryType);
        }

        @Override
        public String toString()
        {
            return toStringHelper(this)
                    .add("resourceGroupId", resourceGroupId)
                    .add("updateTime", updateTime)
                    .add("source", source)
                    .add("environment", environment)
                    .add("queryType", queryType)
                    .toString();
        }
    }
}
