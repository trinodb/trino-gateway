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

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import io.trino.gateway.ha.clustermonitor.ActiveClusterMonitor;
import io.trino.gateway.ha.clustermonitor.ClusterStatsObserver;
import io.trino.gateway.ha.clustermonitor.HealthCheckObserver;
import io.trino.gateway.ha.clustermonitor.TrinoClusterStatsObserver;
import io.trino.gateway.ha.config.HaGatewayConfiguration;
import io.trino.gateway.ha.config.MonitorConfiguration;
import io.trino.gateway.ha.router.BackendStateManager;
import io.trino.gateway.ha.router.RoutingManager;

import java.util.List;

public class ClusterStateListenerModule
        extends AbstractModule
{
    MonitorConfiguration monitorConfig;

    public ClusterStateListenerModule(HaGatewayConfiguration config)
    {
        monitorConfig = config.getMonitor();
    }

    /**
     * Observers to cluster stats updates from
     * {@link ActiveClusterMonitor}.
     */
    @Provides
    @Singleton
    public List<TrinoClusterStatsObserver> getClusterStatsObservers(
            RoutingManager mgr,
            BackendStateManager backendStateManager)
    {
        return ImmutableList.<TrinoClusterStatsObserver>builder()
                .add(new HealthCheckObserver(mgr))
                .add(new ClusterStatsObserver(backendStateManager))
                .build();
    }

    @Provides
    public MonitorConfiguration getMonitorConfiguration()
    {
        return monitorConfig;
    }
}
