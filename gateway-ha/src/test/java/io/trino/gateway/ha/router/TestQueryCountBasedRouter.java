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

import com.google.common.collect.ImmutableList;
import io.trino.gateway.ha.cache.NoopDistributedCache;
import io.trino.gateway.ha.cache.QueryCacheManager;
import io.trino.gateway.ha.clustermonitor.ClusterStats;
import io.trino.gateway.ha.clustermonitor.TrinoStatus;
import io.trino.gateway.ha.config.DataStoreConfiguration;
import io.trino.gateway.ha.config.DatabaseCacheConfiguration;
import io.trino.gateway.ha.config.ProxyBackendConfiguration;
import io.trino.gateway.ha.config.RoutingConfiguration;
import io.trino.gateway.ha.persistence.JdbcConnectionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.trino.gateway.ha.TestingJdbcConnectionManager.createTestingJdbcConnectionManager;
import static io.trino.gateway.ha.TestingJdbcConnectionManager.dataStoreConfig;
import static org.assertj.core.api.Assertions.assertThat;

final class TestQueryCountBasedRouter
{
    static final String BACKEND_URL_1 = "http://c1";
    static final String BACKEND_URL_2 = "http://c2";
    static final String BACKEND_URL_3 = "http://c3";
    static final String BACKEND_URL_4 = "http://c4";
    static final String BACKEND_URL_5 = "http://c5";
    static final String BACKEND_URL_UNHEALTHY = "http://c-unhealthy";

    static final int LEAST_QUEUED_COUNT = 1;
    static final int SAME_QUERY_COUNT = 5;
    GatewayBackendManager backendManager;
    QueryHistoryManager historyManager;
    QueryCountBasedRouter queryCountBasedRouter;
    ImmutableList<ClusterStats> clusters;
    RoutingConfiguration routingConfiguration = new RoutingConfiguration();

    TestQueryCountBasedRouter()
    {
        queryCountBasedRouter = null;
        clusters = null;
    }

    // Helper function to generate the ClusterStat list
    private static List<ClusterStats> getClusterStatsList(String routingGroup)
    {
        ImmutableList.Builder<ClusterStats> clustersBuilder = new ImmutableList.Builder<ClusterStats>();
        // Set Cluster1 stats
        {
            ClusterStats.Builder cluster = ClusterStats.builder("c1-" + routingGroup);
            cluster.proxyTo(BACKEND_URL_1);
            cluster.trinoStatus(TrinoStatus.HEALTHY);
            cluster.routingGroup(routingGroup);
            cluster.runningQueryCount(50);
            cluster.queuedQueryCount(SAME_QUERY_COUNT);
            cluster.userQueuedCount(new HashMap<>(Map.of("u1", 5, "u2", 10, "u3", 2)));
            clustersBuilder.add(cluster.build());
        }
        // Set Cluster2 stats
        {
            ClusterStats.Builder cluster = ClusterStats.builder("c2-" + routingGroup);
            cluster.proxyTo(BACKEND_URL_2);
            cluster.trinoStatus(TrinoStatus.HEALTHY);
            cluster.routingGroup(routingGroup);
            cluster.runningQueryCount(51);
            cluster.queuedQueryCount(SAME_QUERY_COUNT);

            HashMap<String, Integer> userToQueueCount2 = new HashMap<String, Integer>();
            cluster.userQueuedCount(userToQueueCount2);
            cluster.userQueuedCount(new HashMap<>(Map.of("u1", 5, "u2", 1, "u3", 12)));

            clustersBuilder.add(cluster.build());
        }
        // Set Cluster3 stats with the least no of queries running
        {
            ClusterStats.Builder cluster = ClusterStats.builder("c3-" + routingGroup);
            cluster.proxyTo(BACKEND_URL_3);
            cluster.trinoStatus(TrinoStatus.HEALTHY);
            cluster.routingGroup(routingGroup);
            cluster.runningQueryCount(5);
            cluster.queuedQueryCount(SAME_QUERY_COUNT);

            HashMap<String, Integer> userToQueueCount3 = new HashMap<String, Integer>();
            cluster.userQueuedCount(userToQueueCount3);
            cluster.userQueuedCount(new HashMap<>(Map.of("u1", 5, "u2", 2, "u3", 6)));

            clustersBuilder.add(cluster.build());
        }
        // cluster - unhealthy one
        {
            ClusterStats.Builder cluster = ClusterStats.builder("c-unhealthy-" + routingGroup);
            cluster.proxyTo("http://c-unhealthy");
            cluster.trinoStatus(TrinoStatus.UNHEALTHY); //This cluster should never show up to route
            cluster.routingGroup(routingGroup);
            cluster.runningQueryCount(5);
            cluster.queuedQueryCount(SAME_QUERY_COUNT);
            cluster.userQueuedCount(new HashMap<String, Integer>());

            clustersBuilder.add(cluster.build());
        }
        // cluster - unhealthy one, no stats
        {
            ClusterStats.Builder cluster = ClusterStats.builder("c-unhealthy2-" + routingGroup);
            cluster.proxyTo("http://c-unhealthy2");
            cluster.trinoStatus(TrinoStatus.UNHEALTHY); //This cluster should never show up to route

            clustersBuilder.add(cluster.build());
        }
        // cluster - it's messed up - healthy but no stats
        {
            ClusterStats.Builder cluster = ClusterStats.builder("c-unhealthy3-" + routingGroup);
            cluster.proxyTo("http://c-messed-up");
            //This is a scenrio when, something is really wrong
            //We just get the cluster state as health but no stats
            cluster.trinoStatus(TrinoStatus.HEALTHY);
            clustersBuilder.add(cluster.build());
        }

        return clustersBuilder.build();
    }

    private ClusterStats getClusterWithNoUserQueueAndMinQueueCount()
    {
        ClusterStats.Builder cluster = ClusterStats.builder("c-Minimal-Queue");
        cluster.proxyTo(BACKEND_URL_4);
        cluster.trinoStatus(TrinoStatus.HEALTHY);
        cluster.routingGroup("adhoc");
        cluster.runningQueryCount(5);
        cluster.queuedQueryCount(LEAST_QUEUED_COUNT);
        ClusterStats clusterStats = cluster.build();

        backendManager.addBackend(createProxyBackendConfiguration(clusterStats));
        return clusterStats;
    }

    private ClusterStats getClusterWithMinRunningQueries()
    {
        ClusterStats.Builder cluster = ClusterStats.builder("c-Minimal-Running");
        cluster.proxyTo(BACKEND_URL_5);
        cluster.trinoStatus(TrinoStatus.HEALTHY);
        cluster.routingGroup("adhoc");
        cluster.runningQueryCount(1);
        cluster.queuedQueryCount(LEAST_QUEUED_COUNT);
        cluster.userQueuedCount(new HashMap<>());
        ClusterStats clusterStats = cluster.build();

        backendManager.addBackend(createProxyBackendConfiguration(clusterStats));
        return clusterStats;
    }

    static ProxyBackendConfiguration createProxyBackendConfiguration(ClusterStats clusterStats)
    {
        ProxyBackendConfiguration proxyBackend = new ProxyBackendConfiguration();
        proxyBackend.setActive(true);
        proxyBackend.setRoutingGroup(clusterStats.routingGroup() == null ? "unspecified" : clusterStats.routingGroup());
        proxyBackend.setName(clusterStats.clusterId());
        proxyBackend.setProxyTo(clusterStats.proxyTo());
        proxyBackend.setExternalUrl(clusterStats.externalUrl());
        return proxyBackend;
    }

    @BeforeEach
    public void init()
    {
        DataStoreConfiguration dataStoreConfig = dataStoreConfig();
        JdbcConnectionManager connectionManager = createTestingJdbcConnectionManager(dataStoreConfig);
        backendManager = new HaGatewayManager(connectionManager.getJdbi(), routingConfiguration, new DatabaseCacheConfiguration());
        historyManager = new HaQueryHistoryManager(connectionManager.getJdbi(), dataStoreConfig);

        QueryCacheManager.QueryCacheLoader loader = queryId -> {
            String backend = historyManager.getBackendForQueryId(queryId);
            String routingGroup = historyManager.getRoutingGroupForQueryId(queryId);
            String externalUrl = historyManager.getExternalUrlForQueryId(queryId);

            if (backend == null && routingGroup == null && externalUrl == null) {
                return null;
            }
            return new io.trino.gateway.ha.cache.QueryMetadata(backend, routingGroup, externalUrl);
        };
        QueryCacheManager queryCacheManager = new QueryCacheManager(new NoopDistributedCache(), loader);

        queryCountBasedRouter = new QueryCountBasedRouter(backendManager, routingConfiguration, queryCacheManager);
        populateData();
        queryCountBasedRouter.updateClusterStats(clusters);
    }

    private void populateData()
    {
        //Have a adoc and an etl routing groups - 2 sets of clusters
        clusters = new ImmutableList.Builder<ClusterStats>()
                .addAll(getClusterStatsList("adhoc"))
                .addAll(getClusterStatsList("etl"))
                .build();
        clusters.forEach(c -> backendManager.addBackend(createProxyBackendConfiguration(c)));
    }

    @Test
    void testUserWithSameNoOfQueuedQueries()
    {
        // The user u1 has same number of queries queued on each cluster
        // The query needs to be routed to cluster with least number of queries running
        ProxyBackendConfiguration proxyConfig = queryCountBasedRouter.provideBackendConfiguration("etl", "u1");
        String proxyTo = proxyConfig.getProxyTo();

        assertThat(proxyTo).isEqualTo(BACKEND_URL_3);
        assertThat(proxyTo).isNotEqualTo(BACKEND_URL_UNHEALTHY);

        //After the above code is run, c3 cluster has 6 queued queries
        //c1, c2 cluster will be with same original number of queued queries i.e. 5 each
        //The next query should go to the c1 cluster, as it would have less number of cluster wide
        QueryCountBasedRouter.LocalStats c3Stats = queryCountBasedRouter.clusterStats().values().stream()
                .filter(c -> c.clusterId().equals("c3-etl") &&
                        c.routingGroup().equals("etl"))
                .findAny().orElseThrow();
        assertThat(c3Stats.userQueuedCount().getOrDefault("u1", 0))
                .isEqualTo(6);

        proxyConfig = queryCountBasedRouter.provideBackendConfiguration("etl", "u1");
        proxyTo = proxyConfig.getProxyTo();

        assertThat(proxyTo).isEqualTo(BACKEND_URL_1);
        assertThat(proxyTo).isNotEqualTo(BACKEND_URL_UNHEALTHY);
    }

    @Test
    void testUserWithDifferentQueueLengthUser1()
    {
        // The user u2 has different number of queries queued on each cluster
        // The query needs to be routed to cluster with least number of queued for that user
        ProxyBackendConfiguration proxyConfig = queryCountBasedRouter.provideBackendConfiguration(routingConfiguration.getDefaultRoutingGroup(), "u2");
        String proxyTo = proxyConfig.getProxyTo();

        assertThat(BACKEND_URL_2).isEqualTo(proxyTo);
        assertThat(BACKEND_URL_UNHEALTHY).isNotEqualTo(proxyTo);
    }

    @Test
    void testUserWithDifferentQueueLengthUser2()
    {
        ProxyBackendConfiguration proxyConfig = queryCountBasedRouter.provideBackendConfiguration(routingConfiguration.getDefaultRoutingGroup(), "u3");
        String proxyTo = proxyConfig.getProxyTo();

        assertThat(BACKEND_URL_1).isEqualTo(proxyTo);
        assertThat(BACKEND_URL_UNHEALTHY).isNotEqualTo(proxyTo);
    }

    @Test
    void testUserWithNoQueuedQueries()
    {
        ProxyBackendConfiguration proxyConfig = queryCountBasedRouter.provideBackendConfiguration(routingConfiguration.getDefaultRoutingGroup(), "u101");
        String proxyTo = proxyConfig.getProxyTo();

        assertThat(BACKEND_URL_3).isEqualTo(proxyTo);
    }

    @Test
    void testAdhocRoutingGroupFailOver()
    {
        // The ETL routing group doesn't exist
        ProxyBackendConfiguration proxyConfig = queryCountBasedRouter.provideBackendConfiguration("NonExisting", "u1");
        String proxyTo = proxyConfig.getProxyTo();

        assertThat(BACKEND_URL_3).isEqualTo(proxyTo);
        assertThat(BACKEND_URL_UNHEALTHY).isNotEqualTo(proxyTo);
    }

    @Test
    void testClusterWithLeastQueueCount()
    {
        // Add a cluster with minimal queuelength
        clusters = new ImmutableList.Builder<ClusterStats>()
                .addAll(clusters)
                .add(getClusterWithNoUserQueueAndMinQueueCount())
                .build();
        queryCountBasedRouter.updateClusterStats(clusters);

        ProxyBackendConfiguration proxyConfig = queryCountBasedRouter.provideBackendConfiguration("NonExisting", "u1");
        String proxyTo = proxyConfig.getProxyTo();

        assertThat(BACKEND_URL_4).isEqualTo(proxyTo);
        assertThat(BACKEND_URL_UNHEALTHY).isNotEqualTo(proxyTo);
    }

    @Test
    void testClusterWithLeastRunningCount()
    {
        // Add a cluster with minimal queuelength
        clusters = new ImmutableList.Builder<ClusterStats>()
                .addAll(clusters)
                .add(getClusterWithNoUserQueueAndMinQueueCount())
                .add(getClusterWithMinRunningQueries())
                .build();

        queryCountBasedRouter.updateClusterStats(clusters);

        ProxyBackendConfiguration proxyConfig = queryCountBasedRouter.provideBackendConfiguration("NonExisting", "u1");
        String proxyTo = proxyConfig.getProxyTo();

        assertThat(BACKEND_URL_5).isEqualTo(proxyTo);
        assertThat(BACKEND_URL_UNHEALTHY).isNotEqualTo(proxyTo);
    }
}
