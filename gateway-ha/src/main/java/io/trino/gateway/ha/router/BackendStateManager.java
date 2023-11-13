package io.trino.gateway.ha.router;

import io.trino.gateway.ha.clustermonitor.BackendHealthState;
import io.trino.gateway.ha.clustermonitor.ClusterStats;
import io.trino.gateway.ha.config.BackendStateConfiguration;
import io.trino.gateway.ha.config.ProxyBackendConfiguration;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BackendStateManager {
  @Nullable
  private final BackendStateConfiguration configuration;

  private final Map<String, ClusterStats> clusterStats;

  public BackendStateManager(BackendStateConfiguration configuration) {
    this.configuration = configuration;
    this.clusterStats = new HashMap<>();
  }

  public BackendState getBackendState(ProxyBackendConfiguration backend) {
    String name = backend.getName();
    ClusterStats stats = clusterStats.getOrDefault(backend.getName(), new ClusterStats());
    Map<String, Integer> state = new HashMap<>();
    state.put("QUEUED", stats.getQueuedQueryCount());
    state.put("RUNNING", stats.getRunningQueryCount());
    return new BackendState(name, state, stats.getHealthy());
  }

  public BackendStateConfiguration getBackendStateConfiguration() {
    return this.configuration;
  }

  public void updateStates(String clusterId, ClusterStats stats) {
    clusterStats.put(clusterId, stats);
  }

  public void updateHealthState(String clusterId, BackendHealthState state) {
    ClusterStats stats = clusterStats.getOrDefault(clusterId, new ClusterStats());
    stats.setHealthy(state);
    clusterStats.put(clusterId, stats);
  }

  @Data
  public static class BackendState {
    private final String name;
    private final Map<String, Integer> state;
    private final BackendHealthState healthy;

    public BackendState(String name, Map<String, Integer> state, BackendHealthState healthy) {
      this.name = name;
      this.state = state;
      this.healthy = healthy;
    }
  }
}
