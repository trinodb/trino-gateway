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
import org.weakref.jmx.Managed;
import org.weakref.jmx.Nested;

/**
 * Metrics for the OAuth2 token-exchange pin store, in particular
 * {@link #recordPinWriteRateLimited() the per-instance pin-write rate limit} that protects the
 * shared {@code oauth2_routing} table from unauthenticated write amplification (every {@code 401}
 * challenge this instance proxies attempts a write, and that endpoint is reachable without
 * authentication).
 */
public final class OAuth2RoutingStats
{
    private final CounterStat pinWriteRateLimited = new CounterStat();

    public void recordPinWriteRateLimited()
    {
        pinWriteRateLimited.update(1);
    }

    @Managed
    @Nested
    public CounterStat getPinWriteRateLimited()
    {
        return pinWriteRateLimited;
    }
}
