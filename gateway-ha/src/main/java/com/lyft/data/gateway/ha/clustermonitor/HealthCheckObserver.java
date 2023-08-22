package com.lyft.data.gateway.ha.clustermonitor;

import com.lyft.data.gateway.ha.router.RoutingManager;

@lombok.extern.slf4j.Slf4j
public class HealthCheckObserver implements PrestoClusterStatsObserver {
  private final RoutingManager routingManager;

  public HealthCheckObserver(RoutingManager routingManager) {
    this.routingManager = routingManager;
  }

  @Override
  public void observe(java.util.List<ClusterStats> clustersStats) {
    for (ClusterStats clusterStats : clustersStats) {
      routingManager.upateBackEndHealth(clusterStats.getClusterId(), clusterStats.isHealthy());
    }
  }

}
