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
package io.trino.gateway.ha.module;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import io.airlift.log.Logger;
import io.trino.gateway.ha.config.HaGatewayConfiguration;
import io.trino.gateway.ha.config.ScheduleConfiguration;
import io.trino.gateway.ha.router.GatewayBackendManager;
import io.trino.gateway.ha.scheduler.ClusterScheduler;
import jakarta.inject.Singleton;

import static java.util.Objects.requireNonNull;

public class ClusterSchedulerModule
        extends AbstractModule
{
    private static final Logger log = Logger.get(ClusterSchedulerModule.class);
    private final HaGatewayConfiguration configuration;

    // We require all modules to take HaGatewayConfiguration as the only parameter
    public ClusterSchedulerModule(HaGatewayConfiguration configuration)
    {
        this.configuration = requireNonNull(configuration, "configuration is null");
    }

    @Override
    public void configure()
    {
        // Configuration-based binding is handled in the provider methods
        if (configuration.getScheduleConfiguration() != null
                && configuration.getScheduleConfiguration().isEnabled()) {
            log.info("ClusterScheduler configuration is enabled");
        }
        else {
            log.info("ClusterScheduler is disabled or not configured");
        }
    }

    @Provides
    @Singleton
    public ScheduleConfiguration provideScheduleConfiguration()
    {
        return configuration.getScheduleConfiguration();
    }

    @Provides
    @Singleton
    public ClusterScheduler provideClusterScheduler(
            GatewayBackendManager backendManager,
            ScheduleConfiguration scheduleConfiguration)
    {
        if (scheduleConfiguration == null || !scheduleConfiguration.isEnabled()) {
            return null;
        }
        log.info("Creating ClusterScheduler instance");
        return new ClusterScheduler(backendManager, scheduleConfiguration);
    }
}
