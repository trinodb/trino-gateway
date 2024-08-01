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

import io.trino.gateway.ha.clustermonitor.ActiveClusterMonitor;

import java.util.Optional;

public class MonitorConfiguration
{
    private int taskDelaySeconds = ActiveClusterMonitor.MONITOR_TASK_DELAY_SECONDS;

    private Optional<Integer> connectTimeoutSeconds = Optional.empty();

    private Optional<Integer> requestTimeoutSeconds = Optional.empty();

    private Optional<Integer> idleTimeoutSeconds = Optional.empty();

    private int retries;

    public MonitorConfiguration() {}

    public int getTaskDelaySeconds()
    {
        return this.taskDelaySeconds;
    }

    public void setTaskDelaySeconds(int taskDelaySeconds)
    {
        this.taskDelaySeconds = taskDelaySeconds;
    }

    public Optional<Integer> getConnectTimeoutSeconds()
    {
        return connectTimeoutSeconds;
    }

    public void setConnectTimeoutSeconds(Integer connectTimeoutSeconds)
    {
        this.connectTimeoutSeconds = Optional.of(connectTimeoutSeconds);
    }

    public Optional<Integer> getRequestTimeoutSeconds()
    {
        return requestTimeoutSeconds;
    }

    public void setRequestTimeoutSeconds(Integer requestTimeoutSeconds)
    {
        this.requestTimeoutSeconds = Optional.of(requestTimeoutSeconds);
    }

    public Optional<Integer> getIdleTimeoutSeconds()
    {
        return idleTimeoutSeconds;
    }

    public void setIdleTimeoutSeconds(Integer idleTimeoutSeconds)
    {
        this.idleTimeoutSeconds = Optional.of(idleTimeoutSeconds);
    }

    public int getRetries()
    {
        return retries;
    }

    public void setRetries(int retries)
    {
        this.retries = retries;
    }
}
