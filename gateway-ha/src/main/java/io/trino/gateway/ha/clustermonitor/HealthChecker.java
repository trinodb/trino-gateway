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

import io.trino.gateway.ha.notifier.Notifier;

import java.util.List;

public class HealthChecker
        implements TrinoClusterStatsObserver
{
    private static final int MAX_THRESHOLD_QUEUED_QUERY_COUNT = 100;
    private final Notifier notifier;

    public HealthChecker(Notifier notifier)
    {
        this.notifier = notifier;
    }

    @Override
    public void observe(List<ClusterStats> clustersStats)
    {
        for (ClusterStats clusterStats : clustersStats) {
            if (clusterStats.getBackendStatus() == BackendStatus.UNHEALTHY) {
                notifyUnhealthyCluster(clusterStats);
            }
            else {
                if (clusterStats.getQueuedQueryCount() > MAX_THRESHOLD_QUEUED_QUERY_COUNT) {
                    notifyForTooManyQueuedQueries(clusterStats);
                }
                if (clusterStats.getNumWorkerNodes() < 1) {
                    notifyForNoWorkers(clusterStats);
                }
            }
        }
    }

    private void notifyUnhealthyCluster(ClusterStats clusterStats)
    {
        notifier.sendNotification(String.format("%s - Cluster unhealthy",
                        clusterStats.getClusterId()),
                clusterStats.toString());
    }

    private void notifyForTooManyQueuedQueries(ClusterStats clusterStats)
    {
        notifier.sendNotification(String.format("%s - Too many queued queries",
                clusterStats.getClusterId()), clusterStats.toString());
    }

    private void notifyForNoWorkers(ClusterStats clusterStats)
    {
        notifier.sendNotification(String.format("%s - Number of workers",
                clusterStats.getClusterId()), clusterStats.toString());
    }
}
