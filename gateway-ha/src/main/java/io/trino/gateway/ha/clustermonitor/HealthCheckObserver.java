package io.trino.gateway.ha.clustermonitor;

import io.trino.gateway.ha.router.RoutingManager;

public class HealthCheckObserver
        implements TrinoClusterStatsObserver
{
    private final RoutingManager routingManager;

    public HealthCheckObserver(RoutingManager routingManager)
    {
        this.routingManager = routingManager;
    }

    @Override
    public void observe(java.util.List<ClusterStats> clustersStats)
    {
        for (ClusterStats clusterStats : clustersStats) {
            routingManager.upateBackEndHealth(clusterStats.getClusterId(), clusterStats.isHealthy());
            routingManager.updateBackEndHealthDB(clusterStats);
        }
    }
}
