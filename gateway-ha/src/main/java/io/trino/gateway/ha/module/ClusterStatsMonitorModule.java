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
import com.google.inject.Singleton;
import io.airlift.http.client.HttpClient;
import io.trino.gateway.ha.clustermonitor.ClusterStatsHttpMonitor;
import io.trino.gateway.ha.clustermonitor.ClusterStatsInfoApiMonitor;
import io.trino.gateway.ha.clustermonitor.ClusterStatsJdbcMonitor;
import io.trino.gateway.ha.clustermonitor.ClusterStatsMonitor;
import io.trino.gateway.ha.clustermonitor.ForMonitor;
import io.trino.gateway.ha.clustermonitor.NoopClusterStatsMonitor;
import io.trino.gateway.ha.config.ClusterStatsConfiguration;
import io.trino.gateway.ha.config.HaGatewayConfiguration;

import static java.util.Objects.requireNonNull;

public class ClusterStatsMonitorModule
        extends AbstractModule
{
    private final HaGatewayConfiguration config;

    public ClusterStatsMonitorModule(HaGatewayConfiguration config)
    {
        this.config = requireNonNull(config, "config is null");
    }

    @Provides
    @Singleton
    public ClusterStatsMonitor getClusterStatsMonitor(@ForMonitor HttpClient httpClient)
    {
        ClusterStatsConfiguration clusterStatsConfig = config.getClusterStatsConfiguration();
        if (config.getBackendState() == null) {
            return new ClusterStatsInfoApiMonitor(httpClient);
        }
        return switch (clusterStatsConfig.getMonitorType()) {
            case INFO_API -> new ClusterStatsInfoApiMonitor(httpClient);
            case UI_API -> new ClusterStatsHttpMonitor(config.getBackendState());
            case JDBC -> new ClusterStatsJdbcMonitor(config.getBackendState());
            case NOOP -> new NoopClusterStatsMonitor();
        };
    }
}
