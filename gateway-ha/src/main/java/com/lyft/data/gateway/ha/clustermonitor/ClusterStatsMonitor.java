package com.lyft.data.gateway.ha.clustermonitor;

import com.lyft.data.gateway.ha.config.ProxyBackendConfiguration;

public interface ClusterStatsMonitor {
  ClusterStats monitor(ProxyBackendConfiguration backend);
}