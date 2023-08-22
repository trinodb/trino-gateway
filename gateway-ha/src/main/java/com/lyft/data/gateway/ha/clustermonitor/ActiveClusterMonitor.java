package com.lyft.data.gateway.ha.clustermonitor;

import com.google.inject.Inject;
import com.lyft.data.gateway.ha.config.MonitorConfiguration;
import com.lyft.data.gateway.ha.config.ProxyBackendConfiguration;
import com.lyft.data.gateway.ha.router.BackendStateManager;
import com.lyft.data.gateway.ha.router.GatewayBackendManager;
import io.dropwizard.lifecycle.Managed;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@Slf4j
public class ActiveClusterMonitor implements Managed {
  public static final int BACKEND_CONNECT_TIMEOUT_SECONDS = 15;
  public static final int MONITOR_TASK_DELAY_MIN = 1;
  public static final int DEFAULT_THREAD_POOL_SIZE = 20;

  private final List<PrestoClusterStatsObserver> clusterStatsObservers;
  private final GatewayBackendManager gatewayBackendManager;
  private final BackendStateManager backendStateManager;
  private final int connectionTimeout;
  private final int taskDelayMin;

  private volatile boolean monitorActive = true;

  private ExecutorService executorService = Executors.newFixedThreadPool(DEFAULT_THREAD_POOL_SIZE);
  private ExecutorService singleTaskExecutor = Executors.newSingleThreadExecutor();

  private final ClusterStatsMonitor clusterStatsMonitor;

  @Inject
  public ActiveClusterMonitor(
      List<PrestoClusterStatsObserver> clusterStatsObservers,
      GatewayBackendManager gatewayBackendManager,
      MonitorConfiguration monitorConfiguration,
      BackendStateManager backendStateManager,
      ClusterStatsMonitor clusterStatsMonitor) {
    this.clusterStatsObservers = clusterStatsObservers;
    this.gatewayBackendManager = gatewayBackendManager;
    this.connectionTimeout = monitorConfiguration.getConnectionTimeout();
    this.taskDelayMin = monitorConfiguration.getTaskDelayMin();
    this.backendStateManager = backendStateManager;
    this.clusterStatsMonitor = clusterStatsMonitor;
    log.info("Running cluster monitor with connection timeout of {} and task delay of {}",
        connectionTimeout, taskDelayMin);
  }

  /**
   * Run an app that queries all active presto clusters for stats.
   */
  public void start() {
    singleTaskExecutor.submit(
        () -> {
          while (monitorActive) {
            try {
              List<ProxyBackendConfiguration> activeClusters = gatewayBackendManager.getAllActiveBackends();
              List<Future<ClusterStats>> futures = new ArrayList<>();
              for (ProxyBackendConfiguration backend : activeClusters) {
                Future<ClusterStats> call = executorService.submit(() -> clusterStatsMonitor.monitor(backend));
                futures.add(call);
              }
              List<ClusterStats> stats = new ArrayList<>();
              for (Future<ClusterStats> clusterStatsFuture : futures) {
                ClusterStats clusterStats = clusterStatsFuture.get();
                stats.add(clusterStats);
              }

              if (clusterStatsObservers != null) {
                for (PrestoClusterStatsObserver observer : clusterStatsObservers) {
                  observer.observe(stats);
                }
              }

            } catch (Exception e) {
              log.error("Error performing backend monitor tasks", e);
            }
            try {
              Thread.sleep(TimeUnit.MINUTES.toMillis(taskDelayMin));
            } catch (Exception e) {
              log.error("Error with monitor task", e);
            }
          }
        });
  }

  /**
   * Shut down the app.
   */
  public void stop() {
    this.monitorActive = false;
    this.executorService.shutdown();
    this.singleTaskExecutor.shutdown();
  }

}
