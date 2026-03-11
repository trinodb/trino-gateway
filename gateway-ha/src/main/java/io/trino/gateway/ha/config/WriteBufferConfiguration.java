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
    // 10,000 entries at ~500 bytes each ≈ 5 MB; large enough to absorb
    // several minutes of typical query traffic during a DB outage without
    // consuming significant heap.
    private int maxCapacity = 10_000;
    // 2 seconds balances recovery latency against unnecessary DB chatter;
    // short enough that records are durable quickly once the DB is back.
    private Duration flushInterval = new Duration(2, TimeUnit.SECONDS);

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
        if (maxCapacity <= 0) {
            throw new IllegalArgumentException("maxCapacity must be positive");
        }
        this.maxCapacity = maxCapacity;
    }

    public Duration getFlushInterval()
    {
        return flushInterval;
    }

    public void setFlushInterval(Duration flushInterval)
    {
        if (flushInterval.toMillis() <= 0) {
            throw new IllegalArgumentException("flushInterval must be positive");
        }
        this.flushInterval = flushInterval;
    }
}
