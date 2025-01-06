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

import com.google.common.collect.ImmutableMap;
import io.airlift.units.Duration;
import io.trino.gateway.ha.clustermonitor.ActiveClusterMonitor;

import java.util.Map;

import static java.util.concurrent.TimeUnit.SECONDS;

public class MonitorConfiguration
{
    private int taskDelaySeconds = ActiveClusterMonitor.MONITOR_TASK_DELAY_SECONDS;

    private int retries;

    private Duration queryTimeout = new Duration(10, SECONDS);

    private boolean explicitPrepare;

    private String metricsEndpoint = "/metrics";

    // Require 1 node for health by default. This configuration only applies to the ClusterStatsMetricsMonitor
    private Map<String, Float> metricMinimumValues = ImmutableMap.of("trino_metadata_name_DiscoveryNodeManager_ActiveNodeCount", 1f);

    private Map<String, Float> metricMaximumValues = ImmutableMap.of();

    public MonitorConfiguration() {}

    public int getTaskDelaySeconds()
    {
        return this.taskDelaySeconds;
    }

    public void setTaskDelaySeconds(int taskDelaySeconds)
    {
        this.taskDelaySeconds = taskDelaySeconds;
    }

    public int getRetries()
    {
        return retries;
    }

    public void setRetries(int retries)
    {
        this.retries = retries;
    }

    public Duration getQueryTimeout()
    {
        return queryTimeout;
    }

    public void setQueryTimeout(Duration queryTimeout)
    {
        this.queryTimeout = queryTimeout;
    }

    public boolean isExplicitPrepare()
    {
        return explicitPrepare;
    }

    public void setExplicitPrepare(boolean explicitPrepare)
    {
        this.explicitPrepare = explicitPrepare;
    }

    public String getMetricsEndpoint()
    {
        return metricsEndpoint;
    }

    public void setMetricsEndpoint(String metricsEndpoint)
    {
        this.metricsEndpoint = metricsEndpoint;
    }

    public Map<String, Float> getMetricMinimumValues()
    {
        return metricMinimumValues;
    }

    public void setMetricMinimumValues(Map<String, Float> metricMinimumValues)
    {
        this.metricMinimumValues = ImmutableMap.copyOf(metricMinimumValues);
    }

    public Map<String, Float> getMetricMaximumValues()
    {
        return ImmutableMap.copyOf(metricMaximumValues);
    }

    public void setMetricMaximumValues(Map<String, Float> metricMaximumValues)
    {
        this.metricMaximumValues = metricMaximumValues;
    }
}
