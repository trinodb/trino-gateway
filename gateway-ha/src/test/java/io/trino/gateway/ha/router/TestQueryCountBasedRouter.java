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
import io.trino.gateway.ha.clustermonitor.ClusterStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class TestQueryCountBasedRouter
{
    static final String BACKEND_URL_1 = "http://c1";
    static final String BACKEND_URL_2 = "http://c2";
    static final String BACKEND_URL_3 = "http://c3";
    static final String BACKEND_URL_4 = "http://c4";
    static final String BACKEND_URL_5 = "http://c5";
    static final String BACKEND_URL_UNHEALTHY = "http://c-unhealthy";

    static final int LEAST_QUEUED_COUNT = 1;
    static final int SAME_QUERY_COUNT = 5;
    QueryCountBasedRouter queryCountBasedRouter;
    ImmutableList<ClusterStats> clusters;

    TestQueryCountBasedRouter()
    {
        queryCountBasedRouter = null;
        clusters = null;
    }

    // Helper function to generate the ClusterStat list
    private static List<ClusterStats> getClusterStatsList(String routingGroup)
    {
        ImmutableList.Builder<ClusterStats> clustersBuilder = new ImmutableList.Builder();
        // Set Cluster1 stats
        {
            ClusterStats.Builder cluster = ClusterStats.builder("c1");
            cluster.proxyTo(BACKEND_URL_1);
            cluster.healthy(true);
            cluster.routingGroup(routingGroup);
            cluster.runningQueryCount(50);
            cluster.queuedQueryCount(SAME_QUERY_COUNT);
            cluster.userQueuedCount(new HashMap<>(Map.of("u1", 5, "u2", 10, "u3", 2)));
            clustersBuilder.add(cluster.build());
        }
        // Set Cluster2 stats
        {
            ClusterStats.Builder cluster = ClusterStats.builder("c2");
            cluster.proxyTo(BACKEND_URL_2);
            cluster.healthy(true);
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
            ClusterStats.Builder cluster = ClusterStats.builder("c3");
            cluster.proxyTo(BACKEND_URL_3);
            cluster.healthy(true);
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
            ClusterStats.Builder cluster = ClusterStats.builder("c-unhealthy");
            cluster.proxyTo("http://c-unhealthy");
            cluster.healthy(false); //This cluster should never show up to route
            cluster.routingGroup(routingGroup);
            cluster.runningQueryCount(5);
            cluster.queuedQueryCount(SAME_QUERY_COUNT);
            cluster.userQueuedCount(new HashMap<String, Integer>());

            clustersBuilder.add(cluster.build());
        }
        // cluster - unhealthy one, no stats
        {
            ClusterStats.Builder cluster = ClusterStats.builder("c-unhealthy2");
            cluster.proxyTo("http://c-unhealthy2");
            cluster.healthy(false); //This cluster should never show up to route

            clustersBuilder.add(cluster.build());
        }
        // cluster - it's messed up - healthy but no stats
        {
            ClusterStats.Builder cluster = ClusterStats.builder("c-unhealthy2");
            cluster.proxyTo("http://c-messed-up");
            //This is a scenrio when, something is really wrong
            //We just get the cluster state as health but no stats
            cluster.healthy(true);
            clustersBuilder.add(cluster.build());
        }

        return clustersBuilder.build();
    }

    static ClusterStats getClusterWithNoUserQueueAndMinQueueCount()
    {
        ClusterStats.Builder cluster = ClusterStats.builder("c-Minimal-Queue");
        cluster.proxyTo(BACKEND_URL_4);
        cluster.healthy(true);
        cluster.routingGroup("adhoc");
        cluster.runningQueryCount(5);
        cluster.queuedQueryCount(LEAST_QUEUED_COUNT);
        cluster.userQueuedCount(new HashMap<String, Integer>());
        return cluster.build();
    }

    static ClusterStats getClusterWithMinRunnningQueries()
    {
        ClusterStats.Builder cluster = ClusterStats.builder("c-Minimal-Running");
        cluster.proxyTo(BACKEND_URL_5);
        cluster.healthy(true);
        cluster.routingGroup("adhoc");
        cluster.runningQueryCount(1);
        cluster.queuedQueryCount(LEAST_QUEUED_COUNT);
        cluster.userQueuedCount(new HashMap<String, Integer>());
        return cluster.build();
    }

    @BeforeEach
    public void init()
    {
        //Have a adoc and an etl routing groups - 2 sets of clusters
        clusters = new ImmutableList.Builder()
                .addAll(getClusterStatsList("adhoc"))
                .addAll(getClusterStatsList("etl"))
                .build();

        queryCountBasedRouter = new QueryCountBasedRouter(null, null);
        queryCountBasedRouter.upateBackEndStats(clusters);
    }

    @Test
    public void testUserWithSameNoOfQueuedQueries()
    {
        // The user u1 has same number of queries queued on each cluster
        // The query needs to be routed to cluster with least number of queries running
        String proxyTo = queryCountBasedRouter.provideBackendForRoutingGroup("etl", "u1");

        assertThat(proxyTo).isEqualTo(BACKEND_URL_3);
        assertThat(proxyTo).isNotEqualTo(BACKEND_URL_UNHEALTHY);

        //After the above code is run, c3 cluster has 6 queued queries
        //c1, c2 cluster will be with same original number of queued queries i.e. 5 each
        //The next query should go to the c1 cluster, as it would have less number of cluster wide
        QueryCountBasedRouter.LocalStats c3Stats = queryCountBasedRouter.clusterStats().stream()
                .filter(c -> c.clusterId().equals("c3") &&
                        c.routingGroup().equals("etl"))
                .findAny().orElseThrow();
        assertThat(c3Stats.userQueuedCount().getOrDefault("u1", 0))
                .isEqualTo(6);

        proxyTo = queryCountBasedRouter.provideBackendForRoutingGroup("etl", "u1");

        assertThat(proxyTo).isEqualTo(BACKEND_URL_1);
        assertThat(proxyTo).isNotEqualTo(BACKEND_URL_UNHEALTHY);
    }

    @Test
    public void testUserWithDifferentQueueLengthUser1()
    {
        // The user u2 has different number of queries queued on each cluster
        // The query needs to be routed to cluster with least number of queued for that user
        String proxyTo = queryCountBasedRouter.provideAdhocBackend("u2");
        assertThat(BACKEND_URL_2).isEqualTo(proxyTo);
        assertThat(BACKEND_URL_UNHEALTHY).isNotEqualTo(proxyTo);
    }

    @Test
    public void testUserWithDifferentQueueLengthUser2()
    {
        String proxyTo = queryCountBasedRouter.provideAdhocBackend("u3");
        assertThat(BACKEND_URL_1).isEqualTo(proxyTo);
        assertThat(BACKEND_URL_UNHEALTHY).isNotEqualTo(proxyTo);
    }

    @Test
    public void testUserWithNoQueuedQueries()
    {
        String proxyTo = queryCountBasedRouter.provideAdhocBackend("u101");
        assertThat(BACKEND_URL_3).isEqualTo(proxyTo);
    }

    @Test
    public void testAdhocroutingGroupFailOver()
    {
        // The ETL routing group doesn't exist
        String proxyTo = queryCountBasedRouter.provideBackendForRoutingGroup("NonExisting", "u1");
        assertThat(BACKEND_URL_3).isEqualTo(proxyTo);
        assertThat(BACKEND_URL_UNHEALTHY).isNotEqualTo(proxyTo);
    }

    @Test
    public void testClusterWithLeastQueueCount()
    {
        // Add a cluster with minimal queuelength
        clusters = new ImmutableList.Builder()
                .addAll(clusters)
                .add(getClusterWithNoUserQueueAndMinQueueCount())
                .build();
        queryCountBasedRouter.upateBackEndStats(clusters);

        String proxyTo = queryCountBasedRouter.provideBackendForRoutingGroup("NonExisting", "u1");
        assertThat(BACKEND_URL_4).isEqualTo(proxyTo);
        assertThat(BACKEND_URL_UNHEALTHY).isNotEqualTo(proxyTo);
    }

    @Test
    public void testClusterWithLeastRunningCount()
    {
        // Add a cluster with minimal queuelength
        clusters = new ImmutableList.Builder()
                .addAll(clusters)
                .add(getClusterWithNoUserQueueAndMinQueueCount())
                .add(getClusterWithMinRunnningQueries())
                .build();

        queryCountBasedRouter.upateBackEndStats(clusters);

        String proxyTo = queryCountBasedRouter.provideBackendForRoutingGroup("NonExisting", "u1");
        assertThat(BACKEND_URL_5).isEqualTo(proxyTo);
        assertThat(BACKEND_URL_UNHEALTHY).isNotEqualTo(proxyTo);
    }
}
