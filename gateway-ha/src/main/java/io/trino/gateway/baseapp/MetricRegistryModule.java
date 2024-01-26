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
package io.trino.gateway.baseapp;

import com.codahale.metrics.MetricRegistry;
import com.google.inject.AbstractModule;
import jakarta.annotation.Nonnull;

public class MetricRegistryModule
        extends AbstractModule
{
    @Nonnull
    private final MetricRegistry metricsRegistry;

    public MetricRegistryModule(MetricRegistry metricsRegistry)
    {
        this.metricsRegistry = metricsRegistry;
    }

    @Override
    protected void configure()
    {
        bind(MetricRegistry.class).toInstance(metricsRegistry);
    }
}
