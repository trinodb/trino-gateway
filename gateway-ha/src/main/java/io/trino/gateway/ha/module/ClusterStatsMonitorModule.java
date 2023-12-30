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

import com.google.inject.Provides;
import com.google.inject.Singleton;
import io.dropwizard.core.setup.Environment;
import io.trino.gateway.baseapp.AppModule;
import io.trino.gateway.ha.clustermonitor.ClusterStatsHttpMonitor;
import io.trino.gateway.ha.clustermonitor.ClusterStatsJdbcMonitor;
import io.trino.gateway.ha.clustermonitor.ClusterStatsMonitor;
import io.trino.gateway.ha.config.ClusterStatsConfiguration;
import io.trino.gateway.ha.config.HaGatewayConfiguration;

public class ClusterStatsMonitorModule extends AppModule<HaGatewayConfiguration, Environment> {

  private final HaGatewayConfiguration config;

  public ClusterStatsMonitorModule(HaGatewayConfiguration config, Environment env) {
    super(config, env);
    this.config = config;
  }

  @Provides
  @Singleton
  public ClusterStatsMonitor getClusterStatsMonitor() {
    ClusterStatsConfiguration clusterStatsConfig = config.getClusterStatsConfiguration();
    if (clusterStatsConfig.isUseApi()) {
      return new ClusterStatsHttpMonitor(config.getBackendState());
    } else {
      return new ClusterStatsJdbcMonitor(config.getBackendState());
    }
  }
}
