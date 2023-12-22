package io.trino.gateway.ha.clustermonitor;

import io.trino.gateway.ha.config.ProxyBackendConfiguration;

public interface ClusterStatsMonitor
{
    ClusterStats monitor(ProxyBackendConfiguration backend);
}
