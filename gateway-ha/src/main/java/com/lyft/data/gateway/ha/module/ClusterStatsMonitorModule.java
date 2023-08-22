package com.lyft.data.gateway.ha.module;

import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.lyft.data.baseapp.AppModule;
import com.lyft.data.gateway.ha.clustermonitor.ClusterStatsHttpMonitor;
import com.lyft.data.gateway.ha.clustermonitor.ClusterStatsJdbcMonitor;
import com.lyft.data.gateway.ha.clustermonitor.ClusterStatsMonitor;
import com.lyft.data.gateway.ha.config.ClusterStatsConfiguration;
import com.lyft.data.gateway.ha.config.HaGatewayConfiguration;
import io.dropwizard.setup.Environment;

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