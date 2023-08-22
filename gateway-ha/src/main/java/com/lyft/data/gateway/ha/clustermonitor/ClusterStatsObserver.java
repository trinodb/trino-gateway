package com.lyft.data.gateway.ha.clustermonitor;

import java.util.List;

import com.lyft.data.gateway.ha.router.BackendStateManager;

public class ClusterStatsObserver implements PrestoClusterStatsObserver {

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