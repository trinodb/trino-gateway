package io.trino.gateway.ha.router;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.List;
import java.util.Objects;

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

        public boolean equals(final Object o)
        {
            if (o == this) {
                return true;
            }
            if (!(o instanceof ResourceGroupsDetail other)) {
                return false;
            }
            if (!other.canEqual(this)) {
                return false;
            }
            if (this.getResourceGroupId() != other.getResourceGroupId()) {
                return false;
            }
            final Object name = this.getName();
            final Object otherName = other.getName();
            if (!Objects.equals(name, otherName)) {
                return false;
            }
            final Object parent = this.getParent();
            final Object otherParent = other.getParent();
            if (!Objects.equals(parent, otherParent)) {
                return false;
            }
            final Object jmxExport = this.getJmxExport();
            final Object otherJmxExport = other.getJmxExport();
            if (!Objects.equals(jmxExport, otherJmxExport)) {
                return false;
            }
            final Object schedulingPolicy = this.getSchedulingPolicy();
            final Object otherSchedulingPolicy = other.getSchedulingPolicy();
            if (!Objects.equals(schedulingPolicy, otherSchedulingPolicy)) {
                return false;
            }
            final Object schedulingWeight = this.getSchedulingWeight();
            final Object otherSchedulingWeight = other.getSchedulingWeight();
            if (!Objects.equals(schedulingWeight, otherSchedulingWeight)) {
                return false;
            }
            final Object softMemoryLimit = this.getSoftMemoryLimit();
            final Object otherSoftMemoryLimit = other.getSoftMemoryLimit();
            if (!Objects.equals(softMemoryLimit, otherSoftMemoryLimit)) {
                return false;
            }
            if (this.getMaxQueued() != other.getMaxQueued()) {
                return false;
            }
            if (this.getHardConcurrencyLimit() != other.getHardConcurrencyLimit()) {
                return false;
            }
            final Object softConcurrencyLimit = this.getSoftConcurrencyLimit();
            final Object otherSoftConcurrencyLimit = other.getSoftConcurrencyLimit();
            if (!Objects.equals(softConcurrencyLimit, otherSoftConcurrencyLimit)) {
                return false;
            }
            final Object softCpuLimit = this.getSoftCpuLimit();
            final Object otherSoftCpuLimit = other.getSoftCpuLimit();
            if (!Objects.equals(softCpuLimit, otherSoftCpuLimit)) {
                return false;
            }
            final Object hardCpuLimit = this.getHardCpuLimit();
            final Object otherHardCpuLimit = other.getHardCpuLimit();
            if (!Objects.equals(hardCpuLimit, otherHardCpuLimit)) {
                return false;
            }
            final Object environment = this.getEnvironment();
            final Object otherEnvironment = other.getEnvironment();
            return Objects.equals(environment, otherEnvironment);
        }

        protected boolean canEqual(final Object other)
        {
            return other instanceof ResourceGroupsDetail;
        }

        public int hashCode()
        {
            final int prime = 59;
            int result = 1;
            final long resourceGroupId = this.getResourceGroupId();
            result = result * prime + (int) (resourceGroupId >>> 32 ^ resourceGroupId);
            final Object name = this.getName();
            result = result * prime + (name == null ? 43 : name.hashCode());
            final Object parent = this.getParent();
            result = result * prime + (parent == null ? 43 : parent.hashCode());
            final Object jmxExport = this.getJmxExport();
            result = result * prime + (jmxExport == null ? 43 : jmxExport.hashCode());
            final Object schedulingPolicy = this.getSchedulingPolicy();
            result = result * prime + (schedulingPolicy == null ? 43 : schedulingPolicy.hashCode());
            final Object schedulingWeight = this.getSchedulingWeight();
            result = result * prime + (schedulingWeight == null ? 43 : schedulingWeight.hashCode());
            final Object softMemoryLimit = this.getSoftMemoryLimit();
            result = result * prime + (softMemoryLimit == null ? 43 : softMemoryLimit.hashCode());
            result = result * prime + this.getMaxQueued();
            result = result * prime + this.getHardConcurrencyLimit();
            final Object softConcurrencyLimit = this.getSoftConcurrencyLimit();
            result = result * prime + (softConcurrencyLimit == null ? 43 : softConcurrencyLimit.hashCode());
            final Object softCpuLimit = this.getSoftCpuLimit();
            result = result * prime + (softCpuLimit == null ? 43 : softCpuLimit.hashCode());
            final Object hardCpuLimit = this.getHardCpuLimit();
            result = result * prime + (hardCpuLimit == null ? 43 : hardCpuLimit.hashCode());
            final Object environment = this.getEnvironment();
            result = result * prime + (environment == null ? 43 : environment.hashCode());
            return result;
        }

        public String toString()
        {
            return "ResourceGroupsManager.ResourceGroupsDetail(resourceGroupId=" + this.getResourceGroupId() +
                    ", name=" + this.getName() + ", parent=" + this.getParent() + ", jmxExport=" + this.getJmxExport() +
                    ", schedulingPolicy=" + this.getSchedulingPolicy() + ", schedulingWeight=" + this.getSchedulingWeight() +
                    ", softMemoryLimit=" + this.getSoftMemoryLimit() + ", maxQueued=" + this.getMaxQueued() +
                    ", hardConcurrencyLimit=" + this.getHardConcurrencyLimit() + ", softConcurrencyLimit=" + this.getSoftConcurrencyLimit() +
                    ", softCpuLimit=" + this.getSoftCpuLimit() + ", hardCpuLimit=" + this.getHardCpuLimit() +
                    ", environment=" + this.getEnvironment() + ")";
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

        public boolean equals(final Object o)
        {
            if (o == this) {
                return true;
            }
            if (!(o instanceof SelectorsDetail other)) {
                return false;
            }
            if (!other.canEqual(this)) {
                return false;
            }
            if (this.getResourceGroupId() != other.getResourceGroupId()) {
                return false;
            }
            if (this.getPriority() != other.getPriority()) {
                return false;
            }
            final Object userRegex = this.getUserRegex();
            final Object otherUserRegex = other.getUserRegex();
            if (!Objects.equals(userRegex, otherUserRegex)) {
                return false;
            }
            final Object sourceRegex = this.getSourceRegex();
            final Object otherSourceRegex = other.getSourceRegex();
            if (!Objects.equals(sourceRegex, otherSourceRegex)) {
                return false;
            }
            final Object queryType = this.getQueryType();
            final Object otherQueryType = other.getQueryType();
            if (!Objects.equals(queryType, otherQueryType)) {
                return false;
            }
            final Object clientTags = this.getClientTags();
            final Object otherClientTags = other.getClientTags();
            if (!Objects.equals(clientTags, otherClientTags)) {
                return false;
            }
            final Object selectorResourceEstimate = this.getSelectorResourceEstimate();
            final Object otherSelectorResourceEstimate = other.getSelectorResourceEstimate();
            return Objects.equals(selectorResourceEstimate, otherSelectorResourceEstimate);
        }

        protected boolean canEqual(final Object other)
        {
            return other instanceof SelectorsDetail;
        }

        public int hashCode()
        {
            final int prime = 59;
            int result = 1;
            final long resourceGroupId = this.getResourceGroupId();
            result = result * prime + (int) (resourceGroupId >>> 32 ^ resourceGroupId);
            final long priority = this.getPriority();
            result = result * prime + (int) (priority >>> 32 ^ priority);
            final Object userRegex = this.getUserRegex();
            result = result * prime + (userRegex == null ? 43 : userRegex.hashCode());
            final Object sourceRegex = this.getSourceRegex();
            result = result * prime + (sourceRegex == null ? 43 : sourceRegex.hashCode());
            final Object queryType = this.getQueryType();
            result = result * prime + (queryType == null ? 43 : queryType.hashCode());
            final Object clientTags = this.getClientTags();
            result = result * prime + (clientTags == null ? 43 : clientTags.hashCode());
            final Object selectorResourceEstimate = this.getSelectorResourceEstimate();
            result = result * prime + (selectorResourceEstimate == null ? 43 : selectorResourceEstimate.hashCode());
            return result;
        }

        public String toString()
        {
            return "ResourceGroupsManager.SelectorsDetail(resourceGroupId=" + this.getResourceGroupId() +
                    ", priority=" + this.getPriority() + ", userRegex=" + this.getUserRegex() +
                    ", sourceRegex=" + this.getSourceRegex() + ", queryType=" + this.getQueryType() +
                    ", clientTags=" + this.getClientTags() +
                    ", selectorResourceEstimate=" + this.getSelectorResourceEstimate() + ")";
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

        public boolean equals(final Object o)
        {
            if (o == this) {
                return true;
            }
            if (!(o instanceof GlobalPropertiesDetail other)) {
                return false;
            }
            if (!other.canEqual(this)) {
                return false;
            }
            final Object name = this.getName();
            final Object otherName = other.getName();
            if (!Objects.equals(name, otherName)) {
                return false;
            }
            final Object value = this.getValue();
            final Object otherValue = other.getValue();
            return Objects.equals(value, otherValue);
        }

        protected boolean canEqual(final Object other)
        {
            return other instanceof GlobalPropertiesDetail;
        }

        public int hashCode()
        {
            final int prime = 59;
            int result = 1;
            final Object name = this.getName();
            result = result * prime + (name == null ? 43 : name.hashCode());
            final Object value = this.getValue();
            result = result * prime + (value == null ? 43 : value.hashCode());
            return result;
        }

        public String toString()
        {
            return "ResourceGroupsManager.GlobalPropertiesDetail(name=" + this.getName() + ", value=" + this.getValue() + ")";
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

        public boolean equals(final Object o)
        {
            if (o == this) {
                return true;
            }
            if (!(o instanceof ExactSelectorsDetail other)) {
                return false;
            }
            if (!other.canEqual(this)) {
                return false;
            }
            final Object resourceGroupId = this.getResourceGroupId();
            final Object otherResourceGroupId = other.getResourceGroupId();
            if (!Objects.equals(resourceGroupId, otherResourceGroupId)) {
                return false;
            }
            final Object updateTime = this.getUpdateTime();
            final Object otherUpdateTime = other.getUpdateTime();
            if (!Objects.equals(updateTime, otherUpdateTime)) {
                return false;
            }
            final Object source = this.getSource();
            final Object otherSource = other.getSource();
            if (!Objects.equals(source, otherSource)) {
                return false;
            }
            final Object environment = this.getEnvironment();
            final Object otherEnvironment = other.getEnvironment();
            if (!Objects.equals(environment, otherEnvironment)) {
                return false;
            }
            final Object queryType = this.getQueryType();
            final Object otherQueryType = other.getQueryType();
            return Objects.equals(queryType, otherQueryType);
        }

        protected boolean canEqual(final Object other)
        {
            return other instanceof ExactSelectorsDetail;
        }

        public int hashCode()
        {
            final int prime = 59;
            int result = 1;
            final Object resourceGroupId = this.getResourceGroupId();
            result = result * prime + (resourceGroupId == null ? 43 : resourceGroupId.hashCode());
            final Object updateTime = this.getUpdateTime();
            result = result * prime + (updateTime == null ? 43 : updateTime.hashCode());
            final Object source = this.getSource();
            result = result * prime + (source == null ? 43 : source.hashCode());
            final Object environment = this.getEnvironment();
            result = result * prime + (environment == null ? 43 : environment.hashCode());
            final Object queryType = this.getQueryType();
            result = result * prime + (queryType == null ? 43 : queryType.hashCode());
            return result;
        }

        public String toString()
        {
            return "ResourceGroupsManager.ExactSelectorsDetail(resourceGroupId=" + this.getResourceGroupId() +
                    ", updateTime=" + this.getUpdateTime() + ", source=" + this.getSource() +
                    ", environment=" + this.getEnvironment() + ", queryType=" + this.getQueryType() + ")";
        }
    }
}
