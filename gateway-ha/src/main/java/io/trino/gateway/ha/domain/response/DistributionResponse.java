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
package io.trino.gateway.ha.domain.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public class DistributionResponse
{
    private Integer totalBackendCount;
    private Integer offlineBackendCount;
    private Integer onlineBackendCount;
    private Integer healthyBackendCount;
    private Integer unhealthyBackendCount;

    /**
     * Total number of queries.
     * The QueryDistributionRequest's latestHour parameter will affect the statistical range.
     */
    private Long totalQueryCount;

    /**
     * Average number of queries per minute.
     * The QueryDistributionRequest's latestHour parameter will affect the statistical range.
     */
    private Double averageQueryCountMinute;

    /**
     * Average number of queries per second.
     * The QueryDistributionRequest's latestHour parameter will affect the statistical range.
     */
    private Double averageQueryCountSecond;

    /**
     * Pie chart of the number of backend queries.
     * The QueryDistributionRequest's latestHour parameter will affect the statistical range.
     */
    private List<DistributionChart> distributionChart;

    /**
     * Line graph of the number of queries per minute for each backend.
     * The QueryDistributionRequest's latestHour parameter will affect the statistical range.
     */
    private Map<String, List<LineChart>> lineChart;
    private String startTime;

    @JsonProperty
    public Integer getHealthyBackendCount()
    {
        return healthyBackendCount;
    }

    public void setHealthyBackendCount(Integer healthyBackendCount)
    {
        this.healthyBackendCount = healthyBackendCount;
    }

    @JsonProperty
    public Integer getUnhealthyBackendCount()
    {
        return unhealthyBackendCount;
    }

    public void setUnhealthyBackendCount(Integer unhealthyBackendCount)
    {
        this.unhealthyBackendCount = unhealthyBackendCount;
    }

    @JsonProperty
    public String getStartTime()
    {
        return startTime;
    }

    public void setStartTime(String startTime)
    {
        this.startTime = startTime;
    }

    @JsonProperty
    public Integer getTotalBackendCount()
    {
        return totalBackendCount;
    }

    public void setTotalBackendCount(Integer totalBackendCount)
    {
        this.totalBackendCount = totalBackendCount;
    }

    @JsonProperty
    public Integer getOfflineBackendCount()
    {
        return offlineBackendCount;
    }

    public void setOfflineBackendCount(Integer offlineBackendCount)
    {
        this.offlineBackendCount = offlineBackendCount;
    }

    @JsonProperty
    public Integer getOnlineBackendCount()
    {
        return onlineBackendCount;
    }

    public void setOnlineBackendCount(Integer onlineBackendCount)
    {
        this.onlineBackendCount = onlineBackendCount;
    }

    @JsonProperty
    public Long getTotalQueryCount()
    {
        return totalQueryCount;
    }

    public void setTotalQueryCount(Long totalQueryCount)
    {
        this.totalQueryCount = totalQueryCount;
    }

    @JsonProperty
    public Double getAverageQueryCountMinute()
    {
        return averageQueryCountMinute;
    }

    public void setAverageQueryCountMinute(Double averageQueryCountMinute)
    {
        this.averageQueryCountMinute = averageQueryCountMinute;
    }

    @JsonProperty
    public Double getAverageQueryCountSecond()
    {
        return averageQueryCountSecond;
    }

    public void setAverageQueryCountSecond(Double averageQueryCountSecond)
    {
        this.averageQueryCountSecond = averageQueryCountSecond;
    }

    @JsonProperty
    public List<DistributionChart> getDistributionChart()
    {
        return distributionChart;
    }

    public void setDistributionChart(List<DistributionChart> distributionChart)
    {
        this.distributionChart = distributionChart;
    }

    @JsonProperty
    public Map<String, List<LineChart>> getLineChart()
    {
        return lineChart;
    }

    public void setLineChart(Map<String, List<LineChart>> lineChart)
    {
        this.lineChart = lineChart;
    }

    public static class DistributionChart
    {
        private String backendUrl;
        private Long queryCount;
        private String name;

        @JsonProperty
        public String getBackendUrl()
        {
            return backendUrl;
        }

        public void setBackendUrl(String backendUrl)
        {
            this.backendUrl = backendUrl;
        }

        @JsonProperty
        public Long getQueryCount()
        {
            return queryCount;
        }

        public void setQueryCount(Long queryCount)
        {
            this.queryCount = queryCount;
        }

        @JsonProperty
        public String getName()
        {
            return name;
        }

        public void setName(String name)
        {
            this.name = name;
        }
    }

    public static class LineChart
    {
        private String minute;
        private String backendUrl;
        private Long queryCount;
        private String name;

        @JsonProperty
        public String getMinute()
        {
            return minute;
        }

        public void setMinute(String minute)
        {
            this.minute = minute;
        }

        @JsonProperty
        public String getBackendUrl()
        {
            return backendUrl;
        }

        public void setBackendUrl(String backendUrl)
        {
            this.backendUrl = backendUrl;
        }

        @JsonProperty
        public Long getQueryCount()
        {
            return queryCount;
        }

        public void setQueryCount(Long queryCount)
        {
            this.queryCount = queryCount;
        }

        @JsonProperty
        public String getName()
        {
            return name;
        }

        public void setName(String name)
        {
            this.name = name;
        }
    }
}
