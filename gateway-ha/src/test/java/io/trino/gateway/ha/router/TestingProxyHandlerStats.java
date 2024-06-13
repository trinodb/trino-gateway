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

import io.airlift.stats.CounterStat;
import io.trino.gateway.ha.handler.ProxyHandlerStats;

public class TestingProxyHandlerStats
        implements ProxyHandlerStats
{
    private final CounterStat requestCount = new CounterStat();

    @Override
    public void recordRequest()
    {
        requestCount.update(1);
    }

    @Override
    public CounterStat getRequestCount()
    {
        return requestCount;
    }
}
