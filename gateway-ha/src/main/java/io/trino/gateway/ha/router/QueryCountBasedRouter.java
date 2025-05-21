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
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import io.airlift.log.Logger;
import io.trino.gateway.ha.clustermonitor.ClusterStats;
import io.trino.gateway.ha.clustermonitor.TrinoStatus;
import io.trino.gateway.ha.config.HaGatewayConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class QueryCountBasedRouter
        extends StochasticRoutingManager
{
    private static final Logger log = Logger.get(QueryCountBasedRouter.class);
    @GuardedBy("this")
    private List<LocalStats> clusterStats;
    private final String defaultRoutingGroup;

    @VisibleForTesting
    synchronized List<LocalStats> clusterStats()
    {
        return ImmutableList.copyOf(clusterStats);
    }

    static class LocalStats
    {
        private int runningQueryCount;
        private int queuedQueryCount;
        private TrinoStatus trinoStatus;
        private String proxyTo;
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
    }

    public QueryCountBasedRouter(
            GatewayBackendManager gatewayBackendManager,
            QueryHistoryManager queryHistoryManager,
            HaGatewayConfiguration configuration)
    {
        super(gatewayBackendManager, queryHistoryManager);
        clusterStats = new ArrayList<>();
        this.defaultRoutingGroup = configuration.getRouting().getDefaultRoutingGroup();
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

    private synchronized Optional<LocalStats> getClusterToRoute(String user, String routingGroup)
    {
        log.debug("sorting cluster stats for %s %s", user, routingGroup);
        List<LocalStats> filteredList = clusterStats.stream()
                    .filter(stats -> stats.trinoStatus() == TrinoStatus.HEALTHY)
                    .filter(stats -> routingGroup.equals(stats.routingGroup()))
                    .collect(Collectors.toList());

        if (filteredList.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(Collections.min(filteredList, (lhs, rhs) -> compareStats(lhs, rhs, user)));
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

    private synchronized Optional<String> getBackendForRoutingGroup(String routingGroup, String user)
    {
        Optional<LocalStats> cluster = getClusterToRoute(user, routingGroup);
        cluster.ifPresent(c -> updateLocalStats(c, user));
        return cluster.map(c -> c.proxyTo());
    }

    @Override
    public String provideAdhocBackend(String user)
    {
        return getBackendForRoutingGroup(defaultRoutingGroup, user)
                .orElseThrow(() -> new RouterException("did not find any cluster for the default routing group: " + defaultRoutingGroup));
    }

    @Override
    public String provideBackendForRoutingGroup(String routingGroup, String user)
    {
        // Check if the requested routing group exists
        boolean routingGroupExists;
        synchronized (this) {
            routingGroupExists = clusterStats.stream()
                    .anyMatch(stats -> routingGroup.equals(stats.routingGroup()));
        }
        if (!routingGroupExists) {
            throw new RouterException("The router group does not exist: " + routingGroup);
        }
        return getBackendForRoutingGroup(routingGroup, user)
                .orElse(provideAdhocBackend(user));
    }

    @Override
    public synchronized void updateBackEndStats(List<ClusterStats> stats)
    {
        clusterStats = stats.stream().map(a -> new LocalStats(a)).collect(Collectors.toList());
    }
}
