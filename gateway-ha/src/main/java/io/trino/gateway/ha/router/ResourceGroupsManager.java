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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.List;
import java.util.Objects;

import static com.google.common.base.MoreObjects.toStringHelper;

public interface ResourceGroupsManager
{
    ResourceGroupsDetail createResourceGroup(ResourceGroupsDetail resourceGroup, @Nullable String routingGroupDatabase);

    List<ResourceGroupsDetail> readAllResourceGroups(@Nullable String routingGroupDatabase);

    List<ResourceGroupsDetail> readResourceGroup(long resourceGroupId, @Nullable String routingGroupDatabase);

    ResourceGroupsDetail updateResourceGroup(ResourceGroupsDetail resourceGroup, @Nullable String routingGroupDatabase);

    void deleteResourceGroup(long resourceGroupId, @Nullable String routingGroupDatabase);

    SelectorsDetail createSelector(SelectorsDetail selector, @Nullable String routingGroupDatabase);

    List<SelectorsDetail> readAllSelectors(@Nullable String routingGroupDatabase);

    List<SelectorsDetail> readSelector(long resourceGroupId, @Nullable String routingGroupDatabase);

    SelectorsDetail updateSelector(SelectorsDetail selector, SelectorsDetail updatedSelector, @Nullable String routingGroupDatabase);

    void deleteSelector(SelectorsDetail selector, @Nullable String routingGroupDatabase);

    GlobalPropertiesDetail createGlobalProperty(GlobalPropertiesDetail globalPropertyDetail, @Nullable String routingGroupDatabase);

    List<GlobalPropertiesDetail> readAllGlobalProperties(@Nullable String routingGroupDatabase);

    List<GlobalPropertiesDetail> readGlobalProperty(String name, @Nullable String routingGroupDatabase);

    GlobalPropertiesDetail updateGlobalProperty(GlobalPropertiesDetail globalProperty, @Nullable String routingGroupDatabase);

    void deleteGlobalProperty(String name, @Nullable String routingGroupDatabase);

    ExactSelectorsDetail createExactMatchSourceSelector(ExactSelectorsDetail exactSelectorDetail);

    List<ExactSelectorsDetail> readExactMatchSourceSelector();

    ExactSelectorsDetail getExactMatchSourceSelector(ExactSelectorsDetail exactSelectorDetail);

    class ResourceGroupsDetail
            implements Comparable<ResourceGroupsDetail>
    {
        private long resourceGroupId;
        @Nonnull
        private String name;

        /* OPTIONAL POLICY CONTROLS */
        private Long parent;
        private Boolean jmxExport;
        private String schedulingPolicy;
        private Integer schedulingWeight;

        /* REQUIRED QUOTAS */
        @Nonnull
        private String softMemoryLimit;
        private int maxQueued;
        private int hardConcurrencyLimit;

        /* OPTIONAL QUOTAS */
        private Integer softConcurrencyLimit;
        private String softCpuLimit;
        private String hardCpuLimit;
        private String environment;

        public ResourceGroupsDetail() {}

        @JsonCreator
        public ResourceGroupsDetail(
                @JsonProperty("resourceGroupId") long resourceGroupId,
                @JsonProperty("name") @Nonnull String name,
                @JsonProperty("softMemoryLimit") @Nonnull String softMemoryLimit,
                @JsonProperty("maxQueued") int maxQueued,
                @JsonProperty("hardConcurrencyLimit") int hardConcurrencyLimit)
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

        @JsonProperty
        public long getResourceGroupId()
        {
            return this.resourceGroupId;
        }

        public void setResourceGroupId(long resourceGroupId)
        {
            this.resourceGroupId = resourceGroupId;
        }

        @Nonnull
        @JsonProperty
        public String getName()
        {
            return this.name;
        }

        public void setName(@Nonnull String name)
        {
            this.name = name;
        }

        @JsonProperty
        public Long getParent()
        {
            return this.parent;
        }

        public void setParent(Long parent)
        {
            this.parent = parent;
        }

        @JsonProperty
        public Boolean getJmxExport()
        {
            return this.jmxExport;
        }

        public void setJmxExport(Boolean jmxExport)
        {
            this.jmxExport = jmxExport;
        }

        @JsonProperty
        public String getSchedulingPolicy()
        {
            return this.schedulingPolicy;
        }

        public void setSchedulingPolicy(String schedulingPolicy)
        {
            this.schedulingPolicy = schedulingPolicy;
        }

        @JsonProperty
        public Integer getSchedulingWeight()
        {
            return this.schedulingWeight;
        }

        @JsonProperty
        public void setSchedulingWeight(Integer schedulingWeight)
        {
            this.schedulingWeight = schedulingWeight;
        }

        @JsonProperty
        @Nonnull
        public String getSoftMemoryLimit()
        {
            return this.softMemoryLimit;
        }

        public void setSoftMemoryLimit(@Nonnull String softMemoryLimit)
        {
            this.softMemoryLimit = softMemoryLimit;
        }

        @JsonProperty
        public int getMaxQueued()
        {
            return this.maxQueued;
        }

        public void setMaxQueued(int maxQueued)
        {
            this.maxQueued = maxQueued;
        }

        @JsonProperty
        public int getHardConcurrencyLimit()
        {
            return this.hardConcurrencyLimit;
        }

        public void setHardConcurrencyLimit(int hardConcurrencyLimit)
        {
            this.hardConcurrencyLimit = hardConcurrencyLimit;
        }

        @JsonProperty
        public Integer getSoftConcurrencyLimit()
        {
            return this.softConcurrencyLimit;
        }

        public void setSoftConcurrencyLimit(Integer softConcurrencyLimit)
        {
            this.softConcurrencyLimit = softConcurrencyLimit;
        }

        @JsonProperty
        public String getSoftCpuLimit()
        {
            return this.softCpuLimit;
        }

        public void setSoftCpuLimit(String softCpuLimit)
        {
            this.softCpuLimit = softCpuLimit;
        }

        @JsonProperty
        public String getHardCpuLimit()
        {
            return this.hardCpuLimit;
        }

        public void setHardCpuLimit(String hardCpuLimit)
        {
            this.hardCpuLimit = hardCpuLimit;
        }

        @JsonProperty
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
        private long resourceGroupId;
        private long priority;

        private String userRegex;
        private String sourceRegex;

        private String queryType;
        private String clientTags;
        private String selectorResourceEstimate;

        public SelectorsDetail() {}

        @JsonCreator
        public SelectorsDetail(
                @JsonProperty("resourceGroupId") long resourceGroupId,
                @JsonProperty("priority") long priority)
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

        @JsonProperty
        public long getResourceGroupId()
        {
            return this.resourceGroupId;
        }

        public void setResourceGroupId(long resourceGroupId)
        {
            this.resourceGroupId = resourceGroupId;
        }

        @JsonProperty
        public long getPriority()
        {
            return this.priority;
        }

        public void setPriority(long priority)
        {
            this.priority = priority;
        }

        @JsonProperty
        public String getUserRegex()
        {
            return this.userRegex;
        }

        public void setUserRegex(String userRegex)
        {
            this.userRegex = userRegex;
        }

        @JsonProperty
        public String getSourceRegex()
        {
            return this.sourceRegex;
        }

        public void setSourceRegex(String sourceRegex)
        {
            this.sourceRegex = sourceRegex;
        }

        @JsonProperty
        public String getQueryType()
        {
            return this.queryType;
        }

        public void setQueryType(String queryType)
        {
            this.queryType = queryType;
        }

        @JsonProperty
        public String getClientTags()
        {
            return this.clientTags;
        }

        public void setClientTags(String clientTags)
        {
            this.clientTags = clientTags;
        }

        @JsonProperty
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
        @Nonnull
        private String name;
        private String value;

        public GlobalPropertiesDetail() {}

        @JsonCreator
        public GlobalPropertiesDetail(@JsonProperty("name") @Nonnull String name)
        {
            this.name = name;
        }

        @Override
        public int compareTo(GlobalPropertiesDetail o)
        {
            return 0;
        }

        @Nonnull
        @JsonProperty
        public String getName()
        {
            return this.name;
        }

        public void setName(@Nonnull String name)
        {
            this.name = name;
        }

        @JsonProperty
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
        @Nonnull
        private String resourceGroupId;
        @Nonnull
        private String updateTime;

        @Nonnull
        private String source;
        private String environment;
        private String queryType;

        public ExactSelectorsDetail() {}

        @JsonCreator
        public ExactSelectorsDetail(
                @JsonProperty("resourceGroupId") @Nonnull String resourceGroupId,
                @JsonProperty("updateTime") @Nonnull String updateTime,
                @JsonProperty("source") @Nonnull String source)
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

        @JsonProperty
        @Nonnull
        public String getResourceGroupId()
        {
            return this.resourceGroupId;
        }

        public void setResourceGroupId(@Nonnull String resourceGroupId)
        {
            this.resourceGroupId = resourceGroupId;
        }

        @JsonProperty
        @Nonnull
        public String getUpdateTime()
        {
            return this.updateTime;
        }

        public void setUpdateTime(@Nonnull String updateTime)
        {
            this.updateTime = updateTime;
        }

        @JsonProperty
        @Nonnull
        public String getSource()
        {
            return this.source;
        }

        public void setSource(@Nonnull String source)
        {
            this.source = source;
        }

        @JsonProperty
        public String getEnvironment()
        {
            return this.environment;
        }

        public void setEnvironment(String environment)
        {
            this.environment = environment;
        }

        @JsonProperty
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
