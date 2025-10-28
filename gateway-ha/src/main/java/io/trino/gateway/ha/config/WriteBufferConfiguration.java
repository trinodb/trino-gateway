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

import io.airlift.units.Duration;

import java.util.concurrent.TimeUnit;

public class WriteBufferConfiguration
{
    private boolean enabled;
    private int maxCapacity = 10000;
    private Duration flushInterval = new Duration(2, TimeUnit.SECONDS);

    public WriteBufferConfiguration() {}

    public WriteBufferConfiguration(boolean enabled, int maxCapacity, Duration flushInterval)
    {
        this.enabled = enabled;
        this.maxCapacity = maxCapacity;
        this.flushInterval = flushInterval;
    }

    public boolean isEnabled()
    {
        return enabled;
    }

    public void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
    }

    public int getMaxCapacity()
    {
        return maxCapacity;
    }

    public void setMaxCapacity(int maxCapacity)
    {
        this.maxCapacity = maxCapacity;
    }

    public Duration getFlushInterval()
    {
        return flushInterval;
    }

    public void setFlushInterval(Duration flushInterval)
    {
        this.flushInterval = flushInterval;
    }
}
