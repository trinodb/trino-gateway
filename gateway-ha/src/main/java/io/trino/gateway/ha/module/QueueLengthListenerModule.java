package io.trino.gateway.ha.module;

import com.google.inject.Provides;
import com.google.inject.Singleton;
import io.dropwizard.core.setup.Environment;
import io.trino.gateway.baseapp.AppModule;
import io.trino.gateway.ha.clustermonitor.ActiveClusterMonitor;
import io.trino.gateway.ha.clustermonitor.ClusterStatsObserver;
import io.trino.gateway.ha.clustermonitor.HealthCheckObserver;
import io.trino.gateway.ha.clustermonitor.TrinoClusterStatsObserver;
import io.trino.gateway.ha.clustermonitor.TrinoQueueLengthChecker;
import io.trino.gateway.ha.config.HaGatewayConfiguration;
import io.trino.gateway.ha.config.MonitorConfiguration;
import io.trino.gateway.ha.router.BackendStateManager;
import io.trino.gateway.ha.router.TrinoQueueLengthRoutingTable;
import java.util.ArrayList;
import java.util.List;

public class QueueLengthListenerModule extends AppModule<HaGatewayConfiguration, Environment> {
  List<TrinoClusterStatsObserver> observers;
  MonitorConfiguration monitorConfig;

  public QueueLengthListenerModule(HaGatewayConfiguration config, Environment env) {
    super(config, env);
    monitorConfig = config.getMonitor();
  }

  /**
   * Observers to cluster stats updates from
   * {@link ActiveClusterMonitor}.
   *
   * @return
   */
  @Provides
  @Singleton
  public List<TrinoClusterStatsObserver> getClusterStatsObservers(
      TrinoQueueLengthRoutingTable mgr,
      BackendStateManager backendStateManager) {
    observers = new ArrayList<>();
    observers.add(new HealthCheckObserver(mgr));
    observers.add(new ClusterStatsObserver(backendStateManager));
    observers.add(new TrinoQueueLengthChecker(mgr));
    return observers;
  }

  @Provides
  public MonitorConfiguration getMonitorConfiguration() {
    return monitorConfig;
  }
}
