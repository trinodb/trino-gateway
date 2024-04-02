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
package io.trino.gateway.ha.handler;

import com.codahale.metrics.Meter;
import io.airlift.stats.CounterStat;
import io.dropwizard.core.setup.Environment;
import org.weakref.jmx.MBeanExporter;
import org.weakref.jmx.Managed;
import org.weakref.jmx.Nested;

import java.lang.management.ManagementFactory;

import static java.util.Objects.requireNonNull;

public final class ProxyHandlerStats
{
    // Airlift
    private final CounterStat requestCount = new CounterStat();

    // Dropwizard
    private final Meter requestMeter;

    private ProxyHandlerStats(Environment environment, String metricsName)
    {
        this.requestMeter = requireNonNull(environment, "environment is null")
                .metrics()
                .meter(requireNonNull(metricsName, "metricsName is null"));
    }

    public void recordRequest()
    {
        requestCount.update(1);
        requestMeter.mark();
    }

    // Replace this with Guice bind after migrated to Airlift
    public static ProxyHandlerStats create(Environment environment, String metricsName)
    {
        ProxyHandlerStats proxyHandlerStats = new ProxyHandlerStats(environment, metricsName);
        MBeanExporter exporter = new MBeanExporter(ManagementFactory.getPlatformMBeanServer());
        exporter.exportWithGeneratedName(proxyHandlerStats);
        return proxyHandlerStats;
    }

    @Managed
    @Nested
    public CounterStat getRequestCount()
    {
        return requestCount;
    }
}
