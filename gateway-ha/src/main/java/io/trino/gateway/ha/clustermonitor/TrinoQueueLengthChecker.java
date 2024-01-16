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

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import io.trino.gateway.ha.router.TrinoQueueLengthRoutingTable;

import java.util.List;
import java.util.Map;

/**
 * Updates the QueueLength Based Routing Manager {@link TrinoQueueLengthRoutingTable} with
 * updated queue lengths of active clusters.
 */
public class TrinoQueueLengthChecker
        implements TrinoClusterStatsObserver
{
    TrinoQueueLengthRoutingTable routingManager;

    public TrinoQueueLengthChecker(TrinoQueueLengthRoutingTable routingManager)
    {
        this.routingManager = routingManager;
    }

    @Override
    public void observe(List<ClusterStats> stats)
    {
        Table<String, String, Integer> clusterQueueMap = HashBasedTable.create();
        Table<String, String, Integer> clusterRunningMap = HashBasedTable.create();
        Table<String, String, Integer> userClusterQueuedCount = HashBasedTable.create();

        for (ClusterStats stat : stats) {
            if (!stat.isHealthy()) {
                // Skip if the cluster isn't healthy
                continue;
            }
            clusterQueueMap.put(stat.getRoutingGroup(), stat.getClusterId(), stat.getQueuedQueryCount());
            clusterRunningMap.put(stat.getRoutingGroup(), stat.getClusterId(), stat.getRunningQueryCount());

            // Create inverse map from user -> {cluster-> count}
            if (stat.getUserQueuedCount() != null && !stat.getUserQueuedCount().isEmpty()) {
                for (Map.Entry<String, Integer> queueCount : stat.getUserQueuedCount().entrySet()) {
                    userClusterQueuedCount.put(queueCount.getKey(), stat.getClusterId(), queueCount.getValue());
                }
            }
        }

        routingManager.updateRoutingTable(clusterQueueMap, clusterRunningMap, userClusterQueuedCount);
    }
}
