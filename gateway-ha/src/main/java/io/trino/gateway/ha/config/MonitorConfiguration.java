package io.trino.gateway.ha.config;

import io.trino.gateway.ha.clustermonitor.ActiveClusterMonitor;
import lombok.Data;

@Data
public class MonitorConfiguration {
  private int connectionTimeout = ActiveClusterMonitor.BACKEND_CONNECT_TIMEOUT_SECONDS;
  private int taskDelayMin = ActiveClusterMonitor.MONITOR_TASK_DELAY_MIN;
}
