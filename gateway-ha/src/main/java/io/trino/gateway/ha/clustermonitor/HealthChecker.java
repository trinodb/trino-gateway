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

import static java.lang.String.format;

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
            if (!clusterStats.healthy()) {
                notifyUnhealthyCluster(clusterStats);
            }
            else {
                if (clusterStats.queuedQueryCount() > MAX_THRESHOLD_QUEUED_QUERY_COUNT) {
                    notifyForTooManyQueuedQueries(clusterStats);
                }
                if (clusterStats.numWorkerNodes() < 1) {
                    notifyForNoWorkers(clusterStats);
                }
            }
        }
    }

    private void notifyUnhealthyCluster(ClusterStats clusterStats)
    {
        String clusterId = clusterStats.clusterId();
        String subject = format("Cluster name '%s' is unhealthy", clusterId);
        String content = buildContent(clusterStats);
        notifier.sendNotification(subject, content);
    }

    private void notifyForTooManyQueuedQueries(ClusterStats clusterStats)
    {
        String clusterId = clusterStats.clusterId();
        String subject = format("Cluster name '%s' has too many queued queries", clusterId);
        String content = buildContent(clusterStats);
        notifier.sendNotification(subject, content);
    }

    private void notifyForNoWorkers(ClusterStats clusterStats)
    {
        String clusterId = clusterStats.clusterId();
        String subject = format("Cluster name '%s' has no workers running", clusterId);
        String content = buildContent(clusterStats);
        notifier.sendNotification(subject, content);
    }

    private String buildContent(ClusterStats clusterStats)
    {
        return format("""
                Please check below information for the cluster:
                Cluster Id : %s
                Cluster Health : %s
                Routing Group : %s
                Number of Worker Nodes : %s
                Running Queries : %s
                Queued Queries : %s
                User Queued Count : %s
                Proxy To : %s
                External URL : %s
                """,
                clusterStats.clusterId(),
                clusterStats.healthy(),
                clusterStats.routingGroup(),
                clusterStats.numWorkerNodes(),
                clusterStats.runningQueryCount(),
                clusterStats.queuedQueryCount(),
                clusterStats.userQueuedCount(),
                clusterStats.proxyTo(),
                clusterStats.externalUrl());
    }
}
