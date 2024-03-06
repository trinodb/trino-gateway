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
    @JsonProperty
    private Integer totalBackendCount;
    @JsonProperty
    private Integer offlineBackendCount;
    @JsonProperty
    private Integer onlineBackendCount;

    /**
     * Total number of queries.
     * The QueryDistributionRequest's latestHour parameter will affect the statistical range.
     */
    @JsonProperty
    private Long totalQueryCount;

    /**
     * Average number of queries per minute.
     * The QueryDistributionRequest's latestHour parameter will affect the statistical range.
     */
    @JsonProperty
    private Double averageQueryCountMinute;

    /**
     * Average number of queries per second.
     * The QueryDistributionRequest's latestHour parameter will affect the statistical range.
     */
    @JsonProperty
    private Double averageQueryCountSecond;

    /**
     * Pie chart of the number of backend queries.
     * The QueryDistributionRequest's latestHour parameter will affect the statistical range.
     */
    @JsonProperty
    private List<DistributionChart> distributionChart;

    /**
     * Line graph of the number of queries per minute for each backend.
     * The QueryDistributionRequest's latestHour parameter will affect the statistical range.
     */
    @JsonProperty
    private Map<String, List<LineChart>> lineChart;
    @JsonProperty
    private String startTime;

    public String getStartTime()
    {
        return startTime;
    }

    public void setStartTime(String startTime)
    {
        this.startTime = startTime;
    }

    public Integer getTotalBackendCount()
    {
        return totalBackendCount;
    }

    public void setTotalBackendCount(Integer totalBackendCount)
    {
        this.totalBackendCount = totalBackendCount;
    }

    public Integer getOfflineBackendCount()
    {
        return offlineBackendCount;
    }

    public void setOfflineBackendCount(Integer offlineBackendCount)
    {
        this.offlineBackendCount = offlineBackendCount;
    }

    public Integer getOnlineBackendCount()
    {
        return onlineBackendCount;
    }

    public void setOnlineBackendCount(Integer onlineBackendCount)
    {
        this.onlineBackendCount = onlineBackendCount;
    }

    public Long getTotalQueryCount()
    {
        return totalQueryCount;
    }

    public void setTotalQueryCount(Long totalQueryCount)
    {
        this.totalQueryCount = totalQueryCount;
    }

    public Double getAverageQueryCountMinute()
    {
        return averageQueryCountMinute;
    }

    public void setAverageQueryCountMinute(Double averageQueryCountMinute)
    {
        this.averageQueryCountMinute = averageQueryCountMinute;
    }

    public Double getAverageQueryCountSecond()
    {
        return averageQueryCountSecond;
    }

    public void setAverageQueryCountSecond(Double averageQueryCountSecond)
    {
        this.averageQueryCountSecond = averageQueryCountSecond;
    }

    public List<DistributionChart> getDistributionChart()
    {
        return distributionChart;
    }

    public void setDistributionChart(List<DistributionChart> distributionChart)
    {
        this.distributionChart = distributionChart;
    }

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

        public String getBackendUrl()
        {
            return backendUrl;
        }

        public void setBackendUrl(String backendUrl)
        {
            this.backendUrl = backendUrl;
        }

        public Long getQueryCount()
        {
            return queryCount;
        }

        public void setQueryCount(Long queryCount)
        {
            this.queryCount = queryCount;
        }

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

        public String getMinute()
        {
            return minute;
        }

        public void setMinute(String minute)
        {
            this.minute = minute;
        }

        public String getBackendUrl()
        {
            return backendUrl;
        }

        public void setBackendUrl(String backendUrl)
        {
            this.backendUrl = backendUrl;
        }

        public Long getQueryCount()
        {
            return queryCount;
        }

        public void setQueryCount(Long queryCount)
        {
            this.queryCount = queryCount;
        }

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
