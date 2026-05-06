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
package io.trino.gateway.ha.config;

import com.google.common.collect.ImmutableSet;

import java.util.Set;

public class ExplainRoutingConfiguration
{
    private String defaultRoutingGroup = "small";
    private String fallbackRoutingGroup = "small";
    private String smallRoutingGroup = "small";
    private String largeRoutingGroup = "large";
    private String cpuMetricName;
    private String memoryMetricName;
    private double maxSmallClusterCpuPercent = 80;
    private double maxSmallClusterMemoryPercent = 80;

    private ExplainConfiguration explain = new ExplainConfiguration();
    private MetricsThresholds metricsThresholds = new MetricsThresholds();

    public String getDefaultRoutingGroup()
    {
        return defaultRoutingGroup;
    }

    public void setDefaultRoutingGroup(String defaultRoutingGroup)
    {
        this.defaultRoutingGroup = defaultRoutingGroup;
    }

    public String getFallbackRoutingGroup()
    {
        return fallbackRoutingGroup;
    }

    public void setFallbackRoutingGroup(String fallbackRoutingGroup)
    {
        this.fallbackRoutingGroup = fallbackRoutingGroup;
    }

    public String getSmallRoutingGroup()
    {
        return smallRoutingGroup;
    }

    public void setSmallRoutingGroup(String smallRoutingGroup)
    {
        this.smallRoutingGroup = smallRoutingGroup;
    }

    public String getLargeRoutingGroup()
    {
        return largeRoutingGroup;
    }

    public void setLargeRoutingGroup(String largeRoutingGroup)
    {
        this.largeRoutingGroup = largeRoutingGroup;
    }

    public String getCpuMetricName()
    {
        return cpuMetricName;
    }

    public void setCpuMetricName(String cpuMetricName)
    {
        this.cpuMetricName = cpuMetricName;
    }

    public String getMemoryMetricName()
    {
        return memoryMetricName;
    }

    public void setMemoryMetricName(String memoryMetricName)
    {
        this.memoryMetricName = memoryMetricName;
    }

    public double getMaxSmallClusterCpuPercent()
    {
        return maxSmallClusterCpuPercent;
    }

    public void setMaxSmallClusterCpuPercent(double maxSmallClusterCpuPercent)
    {
        this.maxSmallClusterCpuPercent = maxSmallClusterCpuPercent;
    }

    public double getMaxSmallClusterMemoryPercent()
    {
        return maxSmallClusterMemoryPercent;
    }

    public void setMaxSmallClusterMemoryPercent(double maxSmallClusterMemoryPercent)
    {
        this.maxSmallClusterMemoryPercent = maxSmallClusterMemoryPercent;
    }

    public ExplainConfiguration getExplain()
    {
        return explain;
    }

    public void setExplain(ExplainConfiguration explain)
    {
        this.explain = explain;
    }

    public MetricsThresholds getMetricsThresholds()
    {
        return metricsThresholds;
    }

    public void setMetricsThresholds(MetricsThresholds metricsThresholds)
    {
        this.metricsThresholds = metricsThresholds;
    }

    public static class ExplainConfiguration
    {
        private String endpoint;
        private String authorizationHeaderEnv;
        private String trinoUserEnv;
        private String type = "DISTRIBUTED";
        private String format = "JSON";
        private int timeoutSeconds = 10;
        private double pollIntervalSeconds = 0.2;
        private int maxPolls = 25;
        private Set<String> queryTypes = ImmutableSet.of("SELECT", "INSERT", "CREATE_TABLE_AS_SELECT", "MERGE");

        public String getEndpoint()
        {
            return endpoint;
        }

        public void setEndpoint(String endpoint)
        {
            this.endpoint = endpoint;
        }

        public String getAuthorizationHeaderEnv()
        {
            return authorizationHeaderEnv;
        }

        public void setAuthorizationHeaderEnv(String authorizationHeaderEnv)
        {
            this.authorizationHeaderEnv = authorizationHeaderEnv;
        }

        public String getTrinoUserEnv()
        {
            return trinoUserEnv;
        }

        public void setTrinoUserEnv(String trinoUserEnv)
        {
            this.trinoUserEnv = trinoUserEnv;
        }

        public String getType()
        {
            return type;
        }

        public void setType(String type)
        {
            this.type = type;
        }

        public String getFormat()
        {
            return format;
        }

        public void setFormat(String format)
        {
            this.format = format;
        }

        public int getTimeoutSeconds()
        {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds)
        {
            this.timeoutSeconds = timeoutSeconds;
        }

        public double getPollIntervalSeconds()
        {
            return pollIntervalSeconds;
        }

        public void setPollIntervalSeconds(double pollIntervalSeconds)
        {
            this.pollIntervalSeconds = pollIntervalSeconds;
        }

        public int getMaxPolls()
        {
            return maxPolls;
        }

        public void setMaxPolls(int maxPolls)
        {
            this.maxPolls = maxPolls;
        }

        public Set<String> getQueryTypes()
        {
            return queryTypes;
        }

        public void setQueryTypes(Set<String> queryTypes)
        {
            this.queryTypes = queryTypes == null ? ImmutableSet.of() : ImmutableSet.copyOf(queryTypes);
        }
    }

    public static class MetricsThresholds
    {
        private int minStageCount = 3;
        private int minNodeCount = 20;
        private int minJoinCount = 2;
        private int minTableScanCount = 3;
        private int minRemoteExchangeCount = 2;
        private int minAggregateCount = 2;
        private double minCpuCost = 1_000_000;
        private double minMemoryCost = 100_000_000;
        private double minNetworkCost = 100_000_000;
        private double minOutputRowCount = 1_000_000;
        private double minOutputSizeInBytes = 100_000_000;

        public int getMinStageCount()
        {
            return minStageCount;
        }

        public void setMinStageCount(int minStageCount)
        {
            this.minStageCount = minStageCount;
        }

        public int getMinNodeCount()
        {
            return minNodeCount;
        }

        public void setMinNodeCount(int minNodeCount)
        {
            this.minNodeCount = minNodeCount;
        }

        public int getMinJoinCount()
        {
            return minJoinCount;
        }

        public void setMinJoinCount(int minJoinCount)
        {
            this.minJoinCount = minJoinCount;
        }

        public int getMinTableScanCount()
        {
            return minTableScanCount;
        }

        public void setMinTableScanCount(int minTableScanCount)
        {
            this.minTableScanCount = minTableScanCount;
        }

        public int getMinRemoteExchangeCount()
        {
            return minRemoteExchangeCount;
        }

        public void setMinRemoteExchangeCount(int minRemoteExchangeCount)
        {
            this.minRemoteExchangeCount = minRemoteExchangeCount;
        }

        public int getMinAggregateCount()
        {
            return minAggregateCount;
        }

        public void setMinAggregateCount(int minAggregateCount)
        {
            this.minAggregateCount = minAggregateCount;
        }

        public double getMinCpuCost()
        {
            return minCpuCost;
        }

        public void setMinCpuCost(double minCpuCost)
        {
            this.minCpuCost = minCpuCost;
        }

        public double getMinMemoryCost()
        {
            return minMemoryCost;
        }

        public void setMinMemoryCost(double minMemoryCost)
        {
            this.minMemoryCost = minMemoryCost;
        }

        public double getMinNetworkCost()
        {
            return minNetworkCost;
        }

        public void setMinNetworkCost(double minNetworkCost)
        {
            this.minNetworkCost = minNetworkCost;
        }

        public double getMinOutputRowCount()
        {
            return minOutputRowCount;
        }

        public void setMinOutputRowCount(double minOutputRowCount)
        {
            this.minOutputRowCount = minOutputRowCount;
        }

        public double getMinOutputSizeInBytes()
        {
            return minOutputSizeInBytes;
        }

        public void setMinOutputSizeInBytes(double minOutputSizeInBytes)
        {
            this.minOutputSizeInBytes = minOutputSizeInBytes;
        }
    }
}
