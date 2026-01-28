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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import io.trino.gateway.ha.cache.Cache;
import io.trino.gateway.ha.clustermonitor.ClusterStats;
import io.trino.gateway.ha.clustermonitor.TrinoStatus;
import io.trino.gateway.ha.config.ProxyBackendConfiguration;
import io.trino.gateway.ha.config.RoutingConfiguration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class QueryCountBasedRouter
        extends BaseRoutingManager
{
    private ConcurrentHashMap<String, LocalStats> clusterStats;

    @VisibleForTesting
    synchronized Map<String, LocalStats> clusterStats()
    {
        return ImmutableMap.copyOf(clusterStats);
    }

    static class LocalStats
    {
        private int runningQueryCount;
        private int queuedQueryCount;
        private TrinoStatus trinoStatus;
        private String proxyTo;
        private String externalUrl;
        private String routingGroup;
        private String clusterId;
        private Map<String, Integer> userQueuedCount;

        LocalStats(ClusterStats stats)
        {
            clusterId = stats.clusterId();
            runningQueryCount = stats.runningQueryCount();
            queuedQueryCount = stats.queuedQueryCount();
            trinoStatus = stats.trinoStatus();
            proxyTo = stats.proxyTo();
            externalUrl = stats.externalUrl();
            routingGroup = stats.routingGroup();
            if (stats.userQueuedCount() != null) {
                userQueuedCount = new HashMap<String, Integer>(stats.userQueuedCount());
            }
            else {
                userQueuedCount = new HashMap<String, Integer>();
            }
        }

        public String clusterId()
        {
            return clusterId;
        }

        public int runningQueryCount()
        {
            return this.runningQueryCount;
        }

        public void runningQueryCount(int runningQueryCount)
        {
            this.runningQueryCount = runningQueryCount;
        }

        public int queuedQueryCount()
        {
            return this.queuedQueryCount;
        }

        public void queuedQueryCount(int queuedQueryCount)
        {
            this.queuedQueryCount = queuedQueryCount;
        }

        public TrinoStatus trinoStatus()
        {
            return this.trinoStatus;
        }

        public void trinoStatus(TrinoStatus trinoStatus)
        {
            this.trinoStatus = trinoStatus;
        }

        public String proxyTo()
        {
            return this.proxyTo;
        }

        public void proxyTo(String proxyTo)
        {
            this.proxyTo = proxyTo;
        }

        public String getExternalUrl()
        {
            return this.externalUrl;
        }

        public void externalUrl(String externalUrl)
        {
            this.externalUrl = externalUrl;
        }

        public String routingGroup()
        {
            return this.routingGroup;
        }

        public void routingGroup(String routingGroup)
        {
            this.routingGroup = routingGroup;
        }

        public Map<String, Integer> userQueuedCount()
        {
            return this.userQueuedCount;
        }

        public void userQueuedCount(Map<String, Integer> userQueuedCount)
        {
            this.userQueuedCount = userQueuedCount;
        }

        ProxyBackendConfiguration backendConfiguration()
        {
            ProxyBackendConfiguration backendConfiguration = new ProxyBackendConfiguration();
            backendConfiguration.setExternalUrl(externalUrl);
            backendConfiguration.setProxyTo(proxyTo);
            backendConfiguration.setRoutingGroup(routingGroup);
            backendConfiguration.setActive(true);
            return backendConfiguration;
        }
    }

    @Inject
    public QueryCountBasedRouter(
            GatewayBackendManager gatewayBackendManager,
            QueryHistoryManager queryHistoryManager,
            RoutingConfiguration routingConfiguration,
            Cache distributedCache)
    {
        super(gatewayBackendManager, queryHistoryManager, routingConfiguration, distributedCache);
        clusterStats = new ConcurrentHashMap<>();
    }

    private int compareStats(LocalStats lhs, LocalStats rhs, String user)
    {
        // First check if the user has any queries queued
        int compareUserQueue = Integer.compare(lhs.userQueuedCount().getOrDefault(user, 0),
                rhs.userQueuedCount().getOrDefault(user, 0));

        if (compareUserQueue != 0) {
            return compareUserQueue;
        }

        int compareClusterQueue = Integer.compare(lhs.queuedQueryCount(),
                rhs.queuedQueryCount());

        if (compareClusterQueue != 0) {
            return compareClusterQueue;
        }
        // If the user has equal number of queries queued then see which cluster
        // has less number of queries running and route it accordingly
        return Integer.compare(lhs.runningQueryCount(), rhs.runningQueryCount());
    }

    private void updateLocalStats(LocalStats stats, String user)
    {
        // The live stats refresh every few seconds, so we update the stats immediately
        // so that they can be used for next queries to route
        // We assume that if a user has queued queries then newly arriving queries
        // for that user would also be queued
        int count = stats.userQueuedCount() == null ? 0 : stats.userQueuedCount().getOrDefault(user, 0);
        if (count > 0) {
            stats.userQueuedCount().put(user, count + 1);
            return;
        }
        // Else the we assume that the query would be running
        // so update the clusterstat with the +1 running queries
        stats.runningQueryCount(stats.runningQueryCount() + 1);
    }

    @Override
    public synchronized void updateClusterStats(List<ClusterStats> stats)
    {
        super.updateClusterStats(stats);
        for (ClusterStats stat : stats) {
            clusterStats.put(stat.clusterId(), new LocalStats(stat));
        }
    }

    // We sort and find the backend based on the individual user's count of the queued queries
    // first, in case user doesn't have any queries queued we use the cluster wide stats
    //
    // First filter the list of clusters for a particular routing group
    //
    // If a user has queued queries, then find a cluster with the least number of QUEUED queries
    // for that user.
    //
    // If a user's QUEUED query count is the same on every cluster then go with a cluster with
    // the cluster wide stats.
    //
    // Find a cluster with the least number of QUEUED queries, if there are the same number of
    // queries queued, then compare the number of running queries.
    //
    // After a query is routed, we need to update the stats for that cluster until we received the
    // updated stats for all the clusters.
    // if a user has queries queued then we assume that the routed query will be also queued or
    // else we assume it would be scheduled immediately and we increment the stats for the running
    // queries
    @Override
    protected synchronized Optional<ProxyBackendConfiguration> selectBackend(List<ProxyBackendConfiguration> backends, String user)
    {
        Optional<ProxyBackendConfiguration> cluster = backends.stream()
                .filter(backend -> clusterStats.containsKey(backend.getName()))
                .min((a, b) -> compareStats(clusterStats.get(a.getName()), clusterStats.get(b.getName()), user));
        cluster.ifPresent(c -> updateLocalStats(clusterStats.get(c.getName()), user));
        return cluster;
    }
}
