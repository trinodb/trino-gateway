/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.trino.gateway.ha.router;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import io.trino.gateway.ha.clustermonitor.ClusterStats;
import io.trino.gateway.ha.clustermonitor.TrinoStatus;
import io.trino.gateway.ha.config.ProxyBackendConfiguration;
import org.weakref.jmx.MBeanExporter;
import org.weakref.jmx.Managed;

import java.util.HashMap;
import java.util.Map;

import static java.util.Objects.requireNonNull;

public class BackendStateManager
{
    private final MBeanExporter exporter;
    private final Map<String, ClusterStats> clusterStats = new HashMap<>();
    private final Map<String, ClusterStatsJMX> clusterStatsJMXs = new HashMap<>();

    @Inject
    public BackendStateManager(MBeanExporter exporter)
    {
        this.exporter = requireNonNull(exporter, "exporter is null");
    }

    public ClusterStats getBackendState(ProxyBackendConfiguration backend)
    {
        String name = backend.getName();
        return clusterStats.getOrDefault(name, ClusterStats.builder(name).build());
    }

    public void updateStates(String clusterId, ClusterStats stats)
    {
        if (!clusterStatsJMXs.containsKey(clusterId)) {
            ClusterStatsJMX clusterStatsJMX = new ClusterStatsJMX(stats);
            exporter.exportWithGeneratedName(
                    clusterStatsJMX,
                    ClusterStatsJMX.class,
                    ImmutableMap.<String, String>builder()
                            .put("name", "ClusterStats")
                            .put("cluster_id", clusterId)
                            .build());
            clusterStatsJMXs.put(clusterId, clusterStatsJMX);
        }
        else {
            clusterStatsJMXs.get(clusterId).updateFrom(stats);
        }
        clusterStats.put(clusterId, stats);
    }

    public static class ClusterStatsJMX
    {
        private int runningQueryCount;
        private int queuedQueryCount;
        private int numWorkerNodes;
        private TrinoStatus trinoStatus;

        public ClusterStatsJMX(ClusterStats clusterStats)
        {
            updateFrom(clusterStats);
        }

        public void updateFrom(ClusterStats clusterStats)
        {
            runningQueryCount = clusterStats.runningQueryCount();
            queuedQueryCount = clusterStats.queuedQueryCount();
            numWorkerNodes = clusterStats.numWorkerNodes();
            trinoStatus = clusterStats.trinoStatus();
        }

        @Managed
        public int getRunningQueryCount()
        {
            return runningQueryCount;
        }

        @Managed
        public int getQueuedQueryCount()
        {
            return queuedQueryCount;
        }

        @Managed
        public int getNumWorkerNodes()
        {
            return numWorkerNodes;
        }

        @Managed
        public int getTrinoStatusPending()
        {
            return trinoStatus == TrinoStatus.PENDING ? 1 : 0;
        }

        @Managed
        public int getTrinoStatusHealthy()
        {
            return trinoStatus == TrinoStatus.HEALTHY ? 1 : 0;
        }

        @Managed
        public int getTrinoStatusUnhealthy()
        {
            return trinoStatus == TrinoStatus.UNHEALTHY ? 1 : 0;
        }

        @Managed
        public int getTrinoStatusUnknown()
        {
            return trinoStatus == TrinoStatus.UNKNOWN ? 1 : 0;
        }
    }
}
