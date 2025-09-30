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

import com.fasterxml.jackson.annotation.JsonProperty;
import io.airlift.units.Duration;

import java.util.List;

import static java.util.Objects.requireNonNull;

public class ScheduleConfiguration
{
    private boolean enabled;
    private Duration checkInterval = new Duration(5, java.util.concurrent.TimeUnit.MINUTES);
    private String timezone = "GMT"; // Default to GMT if not specified
    private List<ClusterSchedule> schedules;

    @JsonProperty
    public boolean isEnabled()
    {
        return enabled;
    }

    @JsonProperty
    public void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
    }

    @JsonProperty
    public Duration getCheckInterval()
    {
        return checkInterval;
    }

    @JsonProperty
    public void setCheckInterval(Duration checkInterval)
    {
        this.checkInterval = requireNonNull(checkInterval, "checkInterval is null");
    }

    @JsonProperty
    public String getTimezone()
    {
        return timezone;
    }

    @JsonProperty
    public void setTimezone(String timezone)
    {
        this.timezone = requireNonNull(timezone, "timezone is null");
    }

    @JsonProperty
    public List<ClusterSchedule> getSchedules()
    {
        return schedules;
    }

    @JsonProperty
    public void setSchedules(List<ClusterSchedule> schedules)
    {
        this.schedules = schedules;
    }

    public static class ClusterSchedule
    {
        private String clusterName;
        private String cronExpression;
        private boolean activeDuringCron;

        @JsonProperty
        public String getClusterName()
        {
            return clusterName;
        }

        @JsonProperty
        public void setClusterName(String clusterName)
        {
            this.clusterName = requireNonNull(clusterName, "clusterName is null");
        }

        @JsonProperty
        public String getCronExpression()
        {
            return cronExpression;
        }

        @JsonProperty
        public void setCronExpression(String cronExpression)
        {
            this.cronExpression = requireNonNull(cronExpression, "cronExpression is null");
        }

        @JsonProperty
        public boolean isActiveDuringCron()
        {
            return activeDuringCron;
        }

        @JsonProperty
        public void setActiveDuringCron(boolean activeDuringCron)
        {
            this.activeDuringCron = activeDuringCron;
        }
    }
}
