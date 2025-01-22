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
import com.google.errorprone.annotations.concurrent.GuardedBy;
import com.google.inject.Inject;
import io.trino.gateway.ha.clustermonitor.ClusterStats;
import io.trino.gateway.ha.clustermonitor.TrinoStatus;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.weakref.jmx.MBeanExport;
import org.weakref.jmx.MBeanExporter;
import org.weakref.jmx.Managed;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Objects.requireNonNull;

public class BackendStateMBeanExporter
{
    @GuardedBy("this")
    private final List<MBeanExport> mbeanExports = new ArrayList<>();

    private final MBeanExporter exporter;
    private final BackendStateManager backendStateManager;
    private final Map<String, ClusterStatsJMX> backendStates = new HashMap<>();

    @Inject
    public BackendStateMBeanExporter(MBeanExporter exporter, BackendStateManager backendStateManager)
    {
        this.exporter = requireNonNull(exporter, "exporter is null");
        this.backendStateManager = requireNonNull(backendStateManager, "backendStateManager is null");
    }

    @PostConstruct
    public synchronized void updateExport()
    {
        for (ClusterStats clusterStats : backendStateManager.getAllBackendStates().values()) {
            String clusterId = clusterStats.clusterId();

            if (backendStates.containsKey(clusterId)) {
                ClusterStatsJMX clusterStatsJMX = backendStates.get(clusterId);
                clusterStatsJMX.setFrom(clusterStats);
            }
            else {
                ClusterStatsJMX clusterStatsJMX = new ClusterStatsJMX();
                clusterStatsJMX.setFrom(clusterStats);
                mbeanExports.add(exporter.exportWithGeneratedName(
                        clusterStatsJMX,
                        ClusterStatsJMX.class,
                        ImmutableMap.<String, String>builder()
                                .put("name", "ClusterStats")
                                .put("backend", clusterId)
                                .build()));
                backendStates.put(clusterId, clusterStatsJMX);
            }
        }
    }

    @PreDestroy
    public synchronized void unexport()
    {
        for (MBeanExport mbeanExport : mbeanExports) {
            mbeanExport.unexport();
        }
        mbeanExports.clear();
    }

    public static class ClusterStatsJMX
    {
        private int numWorkerNodes;
        private TrinoStatus trinoStatus;
        private String proxyTo;
        private String externalUrl;
        private String routingGroup;

        public void setFrom(ClusterStats clusterStats)
        {
            numWorkerNodes = clusterStats.numWorkerNodes();
            trinoStatus = clusterStats.trinoStatus();
            proxyTo = clusterStats.proxyTo();
            externalUrl = clusterStats.externalUrl();
            routingGroup = clusterStats.routingGroup();
        }

        @Managed
        public int getNumWorkerNodes()
        {
            return numWorkerNodes;
        }

        @Managed
        public boolean isHealthy()
        {
            return trinoStatus == TrinoStatus.HEALTHY;
        }

        @Managed
        public String getProxyTo()
        {
            return proxyTo;
        }

        @Managed
        public String getExternalUrl()
        {
            return externalUrl;
        }

        @Managed
        public String getRoutingGroup()
        {
            return routingGroup;
        }
    }
}
