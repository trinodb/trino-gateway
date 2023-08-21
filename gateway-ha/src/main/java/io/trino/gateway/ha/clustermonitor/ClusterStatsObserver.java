package io.trino.gateway.ha.clustermonitor;

import io.trino.gateway.ha.router.BackendStateManager;

import java.util.List;

public class ClusterStatsObserver implements TrinoClusterStatsObserver {

  private BackendStateManager backendStateManager;

  public ClusterStatsObserver(BackendStateManager backendStateManager) {
    this.backendStateManager = backendStateManager;
  }

  @Override
  public void observe(List<ClusterStats> clustersStats) {
    for (ClusterStats clusterStats : clustersStats) {
      backendStateManager.updateStates(clusterStats.getClusterId(), clusterStats);
    }
  }
}
