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
package io.trino.gateway.ha.clustermonitor;

import io.trino.gateway.ha.router.BackendStateMBeanExporter;
import io.trino.gateway.ha.router.BackendStateManager;

import java.util.List;

import static java.util.Objects.requireNonNull;

public class ClusterStatsObserver
        implements TrinoClusterStatsObserver
{
    private final BackendStateManager backendStateManager;
    private final BackendStateMBeanExporter backendStateMBeanExporter;

    public ClusterStatsObserver(BackendStateManager backendStateManager, BackendStateMBeanExporter backendStateMBeanExporter)
    {
        this.backendStateManager = requireNonNull(backendStateManager, "backendStateManager is null");
        this.backendStateMBeanExporter = requireNonNull(backendStateMBeanExporter, "backendStateMBeanExporter is null");
    }

    @Override
    public void observe(List<ClusterStats> clustersStats)
    {
        for (ClusterStats clusterStats : clustersStats) {
            backendStateManager.updateStates(clusterStats.clusterId(), clusterStats);
        }
        backendStateMBeanExporter.updateExport();
    }
}
