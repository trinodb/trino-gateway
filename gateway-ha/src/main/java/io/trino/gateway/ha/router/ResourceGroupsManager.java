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


import jakarta.annotation.Nullable;
import jakarta.annotation.Nonnull;
import java.util.List;

public interface ResourceGroupsManager {
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

  class ResourceGroupsDetail implements Comparable<ResourceGroupsDetail> {
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
    public int compareTo(ResourceGroupsDetail o) {
      if (this.resourceGroupId < o.resourceGroupId) {
        return 1;
      } else {
        return this.resourceGroupId == o.resourceGroupId ? 0 : -1;
      }
    }

    public @Nonnull long getResourceGroupId()
    {return this.resourceGroupId;}

    public @Nonnull String getName()
    {return this.name;}

    public Long getParent()
    {return this.parent;}

    public Boolean getJmxExport()
    {return this.jmxExport;}

    public String getSchedulingPolicy()
    {return this.schedulingPolicy;}

    public Integer getSchedulingWeight()
    {return this.schedulingWeight;}

    public @Nonnull String getSoftMemoryLimit()
    {return this.softMemoryLimit;}

    public @Nonnull int getMaxQueued()
    {return this.maxQueued;}

    public @Nonnull int getHardConcurrencyLimit()
    {return this.hardConcurrencyLimit;}

    public Integer getSoftConcurrencyLimit()
    {return this.softConcurrencyLimit;}

    public String getSoftCpuLimit()
    {return this.softCpuLimit;}

    public String getHardCpuLimit()
    {return this.hardCpuLimit;}

    public String getEnvironment()
    {return this.environment;}

    public void setResourceGroupId(@Nonnull long resourceGroupId)
    {this.resourceGroupId = resourceGroupId;}

    public void setName(@Nonnull String name)
    {this.name = name;}

    public void setParent(Long parent)
    {this.parent = parent;}

    public void setJmxExport(Boolean jmxExport)
    {this.jmxExport = jmxExport;}

    public void setSchedulingPolicy(String schedulingPolicy)
    {this.schedulingPolicy = schedulingPolicy;}

    public void setSchedulingWeight(Integer schedulingWeight)
    {this.schedulingWeight = schedulingWeight;}

    public void setSoftMemoryLimit(@Nonnull String softMemoryLimit)
    {this.softMemoryLimit = softMemoryLimit;}

    public void setMaxQueued(@Nonnull int maxQueued)
    {this.maxQueued = maxQueued;}

    public void setHardConcurrencyLimit(@Nonnull int hardConcurrencyLimit)
    {this.hardConcurrencyLimit = hardConcurrencyLimit;}

    public void setSoftConcurrencyLimit(Integer softConcurrencyLimit)
    {this.softConcurrencyLimit = softConcurrencyLimit;}

    public void setSoftCpuLimit(String softCpuLimit)
    {this.softCpuLimit = softCpuLimit;}

    public void setHardCpuLimit(String hardCpuLimit)
    {this.hardCpuLimit = hardCpuLimit;}

    public void setEnvironment(String environment)
    {this.environment = environment;}

    public boolean equals(final Object o)
    {
        if (o == this) {
            return true;
        }
        if (!(o instanceof ResourceGroupsDetail)) {
            return false;
        }
        final ResourceGroupsDetail other = (ResourceGroupsDetail) o;
        if (!other.canEqual((Object) this)) {
            return false;
        }
        if (this.getResourceGroupId() != other.getResourceGroupId()) {
            return false;
        }
        final Object this$name = this.getName();
        final Object other$name = other.getName();
        if (this$name == null ? other$name != null : !this$name.equals(other$name)) {
            return false;
        }
        final Object this$parent = this.getParent();
        final Object other$parent = other.getParent();
        if (this$parent == null ? other$parent != null : !this$parent.equals(other$parent)) {
            return false;
        }
        final Object this$jmxExport = this.getJmxExport();
        final Object other$jmxExport = other.getJmxExport();
        if (this$jmxExport == null ? other$jmxExport != null : !this$jmxExport.equals(other$jmxExport)) {
            return false;
        }
        final Object this$schedulingPolicy = this.getSchedulingPolicy();
        final Object other$schedulingPolicy = other.getSchedulingPolicy();
        if (this$schedulingPolicy == null ? other$schedulingPolicy != null : !this$schedulingPolicy.equals(other$schedulingPolicy)) {
            return false;
        }
        final Object this$schedulingWeight = this.getSchedulingWeight();
        final Object other$schedulingWeight = other.getSchedulingWeight();
        if (this$schedulingWeight == null ? other$schedulingWeight != null : !this$schedulingWeight.equals(other$schedulingWeight)) {
            return false;
        }
        final Object this$softMemoryLimit = this.getSoftMemoryLimit();
        final Object other$softMemoryLimit = other.getSoftMemoryLimit();
        if (this$softMemoryLimit == null ? other$softMemoryLimit != null : !this$softMemoryLimit.equals(other$softMemoryLimit)) {
            return false;
        }
        if (this.getMaxQueued() != other.getMaxQueued()) {
            return false;
        }
        if (this.getHardConcurrencyLimit() != other.getHardConcurrencyLimit()) {
            return false;
        }
        final Object this$softConcurrencyLimit = this.getSoftConcurrencyLimit();
        final Object other$softConcurrencyLimit = other.getSoftConcurrencyLimit();
        if (this$softConcurrencyLimit == null ? other$softConcurrencyLimit != null : !this$softConcurrencyLimit.equals(other$softConcurrencyLimit)) {
            return false;
        }
        final Object this$softCpuLimit = this.getSoftCpuLimit();
        final Object other$softCpuLimit = other.getSoftCpuLimit();
        if (this$softCpuLimit == null ? other$softCpuLimit != null : !this$softCpuLimit.equals(other$softCpuLimit)) {
            return false;
        }
        final Object this$hardCpuLimit = this.getHardCpuLimit();
        final Object other$hardCpuLimit = other.getHardCpuLimit();
        if (this$hardCpuLimit == null ? other$hardCpuLimit != null : !this$hardCpuLimit.equals(other$hardCpuLimit)) {
            return false;
        }
        final Object this$environment = this.getEnvironment();
        final Object other$environment = other.getEnvironment();
        if (this$environment == null ? other$environment != null : !this$environment.equals(other$environment)) {
            return false;
        }
        return true;
    }

    protected boolean canEqual(final Object other)
    {return other instanceof ResourceGroupsDetail;}

    public int hashCode()
    {
        final int PRIME = 59;
        int result = 1;
        final long $resourceGroupId = this.getResourceGroupId();
        result = result * PRIME + (int) ($resourceGroupId >>> 32 ^ $resourceGroupId);
        final Object $name = this.getName();
        result = result * PRIME + ($name == null ? 43 : $name.hashCode());
        final Object $parent = this.getParent();
        result = result * PRIME + ($parent == null ? 43 : $parent.hashCode());
        final Object $jmxExport = this.getJmxExport();
        result = result * PRIME + ($jmxExport == null ? 43 : $jmxExport.hashCode());
        final Object $schedulingPolicy = this.getSchedulingPolicy();
        result = result * PRIME + ($schedulingPolicy == null ? 43 : $schedulingPolicy.hashCode());
        final Object $schedulingWeight = this.getSchedulingWeight();
        result = result * PRIME + ($schedulingWeight == null ? 43 : $schedulingWeight.hashCode());
        final Object $softMemoryLimit = this.getSoftMemoryLimit();
        result = result * PRIME + ($softMemoryLimit == null ? 43 : $softMemoryLimit.hashCode());
        result = result * PRIME + this.getMaxQueued();
        result = result * PRIME + this.getHardConcurrencyLimit();
        final Object $softConcurrencyLimit = this.getSoftConcurrencyLimit();
        result = result * PRIME + ($softConcurrencyLimit == null ? 43 : $softConcurrencyLimit.hashCode());
        final Object $softCpuLimit = this.getSoftCpuLimit();
        result = result * PRIME + ($softCpuLimit == null ? 43 : $softCpuLimit.hashCode());
        final Object $hardCpuLimit = this.getHardCpuLimit();
        result = result * PRIME + ($hardCpuLimit == null ? 43 : $hardCpuLimit.hashCode());
        final Object $environment = this.getEnvironment();
        result = result * PRIME + ($environment == null ? 43 : $environment.hashCode());
        return result;
    }

    public String toString()
        {return "ResourceGroupsManager.ResourceGroupsDetail(resourceGroupId=" + this.getResourceGroupId() +
                ", name=" + this.getName() + ", parent=" + this.getParent() + ", jmxExport=" + this.getJmxExport() +
                ", schedulingPolicy=" + this.getSchedulingPolicy() + ", schedulingWeight=" + this.getSchedulingWeight() +
                ", softMemoryLimit=" + this.getSoftMemoryLimit() + ", maxQueued=" + this.getMaxQueued() +
                ", hardConcurrencyLimit=" + this.getHardConcurrencyLimit() + ", softConcurrencyLimit=" + this.getSoftConcurrencyLimit() +
                ", softCpuLimit=" + this.getSoftCpuLimit() + ", hardCpuLimit=" + this.getHardCpuLimit() +
                ", environment=" + this.getEnvironment() + ")";}
  }

  class SelectorsDetail implements Comparable<SelectorsDetail> {
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
    public int compareTo(SelectorsDetail o) {
      if (this.resourceGroupId < o.resourceGroupId) {
        return 1;
      } else {
        return this.resourceGroupId == o.resourceGroupId ? 0 : -1;
      }
    }

    public @Nonnull long getResourceGroupId()
    {return this.resourceGroupId;}

    public @Nonnull long getPriority()
    {return this.priority;}

    public String getUserRegex()
    {return this.userRegex;}

    public String getSourceRegex()
    {return this.sourceRegex;}

    public String getQueryType()
    {return this.queryType;}

    public String getClientTags()
    {return this.clientTags;}

    public String getSelectorResourceEstimate()
    {return this.selectorResourceEstimate;}

    public void setResourceGroupId(@Nonnull long resourceGroupId)
    {this.resourceGroupId = resourceGroupId;}

    public void setPriority(@Nonnull long priority)
    {this.priority = priority;}

    public void setUserRegex(String userRegex)
    {this.userRegex = userRegex;}

    public void setSourceRegex(String sourceRegex)
    {this.sourceRegex = sourceRegex;}

    public void setQueryType(String queryType)
    {this.queryType = queryType;}

    public void setClientTags(String clientTags)
    {this.clientTags = clientTags;}

    public void setSelectorResourceEstimate(String selectorResourceEstimate)
    {this.selectorResourceEstimate = selectorResourceEstimate;}

    public boolean equals(final Object o)
    {
        if (o == this) {
            return true;
        }
        if (!(o instanceof SelectorsDetail)) {
            return false;
        }
        final SelectorsDetail other = (SelectorsDetail) o;
        if (!other.canEqual((Object) this)) {
            return false;
        }
        if (this.getResourceGroupId() != other.getResourceGroupId()) {
            return false;
        }
        if (this.getPriority() != other.getPriority()) {
            return false;
        }
        final Object this$userRegex = this.getUserRegex();
        final Object other$userRegex = other.getUserRegex();
        if (this$userRegex == null ? other$userRegex != null : !this$userRegex.equals(other$userRegex)) {
            return false;
        }
        final Object this$sourceRegex = this.getSourceRegex();
        final Object other$sourceRegex = other.getSourceRegex();
        if (this$sourceRegex == null ? other$sourceRegex != null : !this$sourceRegex.equals(other$sourceRegex)) {
            return false;
        }
        final Object this$queryType = this.getQueryType();
        final Object other$queryType = other.getQueryType();
        if (this$queryType == null ? other$queryType != null : !this$queryType.equals(other$queryType)) {
            return false;
        }
        final Object this$clientTags = this.getClientTags();
        final Object other$clientTags = other.getClientTags();
        if (this$clientTags == null ? other$clientTags != null : !this$clientTags.equals(other$clientTags)) {
            return false;
        }
        final Object this$selectorResourceEstimate = this.getSelectorResourceEstimate();
        final Object other$selectorResourceEstimate = other.getSelectorResourceEstimate();
        if (this$selectorResourceEstimate == null ? other$selectorResourceEstimate != null : !this$selectorResourceEstimate.equals(other$selectorResourceEstimate)) {
            return false;
        }
        return true;
    }

    protected boolean canEqual(final Object other)
    {return other instanceof SelectorsDetail;}

    public int hashCode()
    {
        final int PRIME = 59;
        int result = 1;
        final long $resourceGroupId = this.getResourceGroupId();
        result = result * PRIME + (int) ($resourceGroupId >>> 32 ^ $resourceGroupId);
        final long $priority = this.getPriority();
        result = result * PRIME + (int) ($priority >>> 32 ^ $priority);
        final Object $userRegex = this.getUserRegex();
        result = result * PRIME + ($userRegex == null ? 43 : $userRegex.hashCode());
        final Object $sourceRegex = this.getSourceRegex();
        result = result * PRIME + ($sourceRegex == null ? 43 : $sourceRegex.hashCode());
        final Object $queryType = this.getQueryType();
        result = result * PRIME + ($queryType == null ? 43 : $queryType.hashCode());
        final Object $clientTags = this.getClientTags();
        result = result * PRIME + ($clientTags == null ? 43 : $clientTags.hashCode());
        final Object $selectorResourceEstimate = this.getSelectorResourceEstimate();
        result = result * PRIME + ($selectorResourceEstimate == null ? 43 : $selectorResourceEstimate.hashCode());
        return result;
    }

    public String toString() {
        return "ResourceGroupsManager.SelectorsDetail(resourceGroupId=" + this.getResourceGroupId() +
                ", priority=" + this.getPriority() + ", userRegex=" + this.getUserRegex() +
                ", sourceRegex=" + this.getSourceRegex() + ", queryType=" + this.getQueryType() +
                ", clientTags=" + this.getClientTags() +
                ", selectorResourceEstimate=" + this.getSelectorResourceEstimate() + ")";}
  }

  class GlobalPropertiesDetail implements Comparable<GlobalPropertiesDetail> {
    @Nonnull private String name;
    private String value;

    public GlobalPropertiesDetail() {}

    public GlobalPropertiesDetail(@Nonnull String name)
    {
        this.name = name;
    }

    @Override
    public int compareTo(GlobalPropertiesDetail o) {
      return 0;
    }

    public @Nonnull String getName()
    {return this.name;}

    public String getValue()
    {return this.value;}

    public void setName(@Nonnull String name)
    {this.name = name;}

    public void setValue(String value)
    {this.value = value;}

    public boolean equals(final Object o)
    {
        if (o == this) {
            return true;
        }
        if (!(o instanceof GlobalPropertiesDetail)) {
            return false;
        }
        final GlobalPropertiesDetail other = (GlobalPropertiesDetail) o;
        if (!other.canEqual((Object) this)) {
            return false;
        }
        final Object this$name = this.getName();
        final Object other$name = other.getName();
        if (this$name == null ? other$name != null : !this$name.equals(other$name)) {
            return false;
        }
        final Object this$value = this.getValue();
        final Object other$value = other.getValue();
        if (this$value == null ? other$value != null : !this$value.equals(other$value)) {
            return false;
        }
        return true;
    }

    protected boolean canEqual(final Object other)
    {return other instanceof GlobalPropertiesDetail;}

    public int hashCode()
    {
        final int PRIME = 59;
        int result = 1;
        final Object $name = this.getName();
        result = result * PRIME + ($name == null ? 43 : $name.hashCode());
        final Object $value = this.getValue();
        result = result * PRIME + ($value == null ? 43 : $value.hashCode());
        return result;
    }

    public String toString()
    {return "ResourceGroupsManager.GlobalPropertiesDetail(name=" + this.getName() + ", value=" + this.getValue() + ")";}
  }

  class ExactSelectorsDetail implements Comparable<ExactSelectorsDetail> {
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
    public int compareTo(ExactSelectorsDetail o) {
      return 0;
    }

    public @Nonnull String getResourceGroupId()
    {return this.resourceGroupId;}

    public @Nonnull String getUpdateTime()
    {return this.updateTime;}

    public @Nonnull String getSource()
    {return this.source;}

    public String getEnvironment()
    {return this.environment;}

    public String getQueryType()
    {return this.queryType;}

    public void setResourceGroupId(@Nonnull String resourceGroupId)
    {this.resourceGroupId = resourceGroupId;}

    public void setUpdateTime(@Nonnull String updateTime)
    {this.updateTime = updateTime;}

    public void setSource(@Nonnull String source)
    {this.source = source;}

    public void setEnvironment(String environment)
    {this.environment = environment;}

    public void setQueryType(String queryType)
    {this.queryType = queryType;}

    public boolean equals(final Object o)
    {
        if (o == this) {
            return true;
        }
        if (!(o instanceof ExactSelectorsDetail)) {
            return false;
        }
        final ExactSelectorsDetail other = (ExactSelectorsDetail) o;
        if (!other.canEqual((Object) this)) {
            return false;
        }
        final Object this$resourceGroupId = this.getResourceGroupId();
        final Object other$resourceGroupId = other.getResourceGroupId();
        if (this$resourceGroupId == null ? other$resourceGroupId != null : !this$resourceGroupId.equals(other$resourceGroupId)) {
            return false;
        }
        final Object this$updateTime = this.getUpdateTime();
        final Object other$updateTime = other.getUpdateTime();
        if (this$updateTime == null ? other$updateTime != null : !this$updateTime.equals(other$updateTime)) {
            return false;
        }
        final Object this$source = this.getSource();
        final Object other$source = other.getSource();
        if (this$source == null ? other$source != null : !this$source.equals(other$source)) {
            return false;
        }
        final Object this$environment = this.getEnvironment();
        final Object other$environment = other.getEnvironment();
        if (this$environment == null ? other$environment != null : !this$environment.equals(other$environment)) {
            return false;
        }
        final Object this$queryType = this.getQueryType();
        final Object other$queryType = other.getQueryType();
        if (this$queryType == null ? other$queryType != null : !this$queryType.equals(other$queryType)) {
            return false;
        }
        return true;
    }

    protected boolean canEqual(final Object other)
    {return other instanceof ExactSelectorsDetail;}

    public int hashCode()
    {
        final int PRIME = 59;
        int result = 1;
        final Object $resourceGroupId = this.getResourceGroupId();
        result = result * PRIME + ($resourceGroupId == null ? 43 : $resourceGroupId.hashCode());
        final Object $updateTime = this.getUpdateTime();
        result = result * PRIME + ($updateTime == null ? 43 : $updateTime.hashCode());
        final Object $source = this.getSource();
        result = result * PRIME + ($source == null ? 43 : $source.hashCode());
        final Object $environment = this.getEnvironment();
        result = result * PRIME + ($environment == null ? 43 : $environment.hashCode());
        final Object $queryType = this.getQueryType();
        result = result * PRIME + ($queryType == null ? 43 : $queryType.hashCode());
        return result;
    }

    public String toString(){
        return "ResourceGroupsManager.ExactSelectorsDetail(resourceGroupId=" + this.getResourceGroupId() +
                ", updateTime=" + this.getUpdateTime() + ", source=" + this.getSource() +
                ", environment=" + this.getEnvironment() + ", queryType=" + this.getQueryType() + ")";}
  }
}
