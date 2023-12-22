package io.trino.gateway.ha.clustermonitor;

import java.util.List;

public interface TrinoClusterStatsObserver
{
    void observe(List<ClusterStats> stats);
}
