package com.lyft.data.gateway.ha.module;

import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.lyft.data.baseapp.AppModule;
import com.lyft.data.gateway.ha.clustermonitor.ClusterStatsObserver;
import com.lyft.data.gateway.ha.clustermonitor.HealthCheckObserver;
import com.lyft.data.gateway.ha.clustermonitor.PrestoClusterStatsObserver;
import com.lyft.data.gateway.ha.config.HaGatewayConfiguration;
import com.lyft.data.gateway.ha.config.MonitorConfiguration;
import com.lyft.data.gateway.ha.config.NotifierConfiguration;
import com.lyft.data.gateway.ha.router.BackendStateManager;
import com.lyft.data.gateway.ha.router.RoutingManager;
import io.dropwizard.setup.Environment;
import java.util.ArrayList;
import java.util.List;

public class ClusterStateListenerModule extends AppModule<HaGatewayConfiguration, Environment> {
  List<PrestoClusterStatsObserver> observers;
  MonitorConfiguration monitorConfig;

  public ClusterStateListenerModule(HaGatewayConfiguration config, Environment env) {
    super(config, env);
    monitorConfig = config.getMonitor();
  }

  /**
   * Observers to cluster stats updates from
   * {@link com.lyft.data.gateway.ha.clustermonitor.ActiveClusterMonitor}.
   *
   * @return
   */
  @Provides
  @Singleton
  public List<PrestoClusterStatsObserver> getClusterStatsObservers(RoutingManager mgr,
      BackendStateManager backendStateManager) {
    observers = new ArrayList<>();
    NotifierConfiguration notifierConfiguration = getConfiguration().getNotifier();
    observers.add(new HealthCheckObserver(mgr));
    observers.add(new ClusterStatsObserver(backendStateManager));

    return observers;
  }

  @Provides
  public MonitorConfiguration getMonitorConfiguration() {
    return monitorConfig;
  }
}
