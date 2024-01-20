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

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import io.trino.gateway.ha.config.ProxyBackendConfiguration;
import io.trino.gateway.ha.persistence.JdbcConnectionManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.trino.gateway.ha.TestingJdbcConnectionManager.createTestingJdbcConnectionManager;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(Lifecycle.PER_CLASS)
public class TestTrinoQueueLengthRoutingTable
{
    static final int[] QUERY_VOLUMES = {15, 50, 100, 200};
    static final int NUM_BACKENDS = 5;
    TrinoQueueLengthRoutingTable routingTable;
    GatewayBackendManager backendManager;
    QueryHistoryManager historyManager;
    String[] mockRoutingGroups = {"adhoc", "scheduled"};
    String mockRoutingGroup = "adhoc";

    String mockUser = "user";

    Table<String, String, Integer> clusterQueueMap;
    Table<String, String, Integer> clusterRunningMap;

    @BeforeAll
    public void setUp()
    {
        JdbcConnectionManager connectionManager = createTestingJdbcConnectionManager();
        backendManager = new HaGatewayManager(connectionManager);
        historyManager = new HaQueryHistoryManager(connectionManager);
        routingTable = new TrinoQueueLengthRoutingTable(backendManager, historyManager);

        for (String grp : mockRoutingGroups) {
            addMockBackends(grp, NUM_BACKENDS);
        }
    }

    private void deactiveAllBackends()
    {
        for (int i = 0; i < NUM_BACKENDS; i++) {
            backendManager.deactivateBackend(mockRoutingGroup + i);
        }
        clusterQueueMap = HashBasedTable.create();
        clusterRunningMap = HashBasedTable.create();
    }

    private void addMockBackends(String groupName, int numBackends)
    {
        String backend = null;

        for (int i = 0; i < numBackends; i++) {
            backend = groupName + i;
            ProxyBackendConfiguration proxyBackend = new ProxyBackendConfiguration();
            proxyBackend.setActive(true);
            proxyBackend.setRoutingGroup(groupName);
            proxyBackend.setName(backend);
            proxyBackend.setProxyTo(backend + ".gateway.trino.io");
            proxyBackend.setExternalUrl("gateway.trino.io");
            backendManager.addBackend(proxyBackend);
        }
    }

    private void registerBackEndsWithRandomQueueLengths(String groupName, int numBackends)
    {
        int mockQueueLength = 0;
        String backend;
        Random rand = new Random();

        for (int i = 0; i < numBackends; i++) {
            backend = groupName + i;
            backendManager.activateBackend(backend);
            mockQueueLength = mockQueueLength + rand.nextInt(100);
            clusterQueueMap.put(groupName, backend, mockQueueLength);
        }

        // Running counts don't matter if queue lengths are random.
        routingTable.updateRoutingTable(clusterQueueMap, clusterRunningMap, null);
    }

    private void registerBackEnds(String groupName, int numBackends,
            int queueLengthDistributiveFactor,
            int runningLenDistributiveFactor)
    {
        int mockQueueLength = 0;
        int mockRunningLength = 0;
        String backend;

        for (int i = 0; i < numBackends; i++) {
            backend = groupName + i;
            backendManager.activateBackend(backend);
            mockQueueLength = mockQueueLength + queueLengthDistributiveFactor;
            mockRunningLength = mockRunningLength + runningLenDistributiveFactor;
            clusterQueueMap.put(groupName, backend, mockQueueLength);
            clusterRunningMap.put(groupName, backend, mockRunningLength);
        }

        routingTable.updateRoutingTable(clusterQueueMap, clusterRunningMap, null);
    }

    private void registerBackEndsWithUserQueue(String groupName, int numBackends,
            List<Integer> userQueues)
    {
        deactiveAllBackends();
        int mockQueueLength = 0;
        int mockRunningLength = 0;
        String backend;

        Map<String, Integer> queueLengths = new HashMap<>();
        Map<String, Integer> runningLengths = new HashMap<>();
        Table<String, String, Integer> userClusterQueue = HashBasedTable.create();

        for (int i = 0; i < numBackends; i++) {
            backend = groupName + i;
            backendManager.activateBackend(backend);
            clusterQueueMap.put(groupName, backend, mockQueueLength);
            runningLengths.put(backend, mockRunningLength);
            clusterRunningMap.put(groupName, backend, mockRunningLength);
            if (userQueues.size() > i) {
                userClusterQueue.put(mockUser, backend, userQueues.get(i));
            }
        }

        routingTable.updateRoutingTable(clusterQueueMap, clusterRunningMap, userClusterQueue);
    }

    private void resetBackends(String groupName, int numBk,
            int queueDistribution, int runningDistribution)
    {
        deactiveAllBackends();
        registerBackEnds(groupName, numBk, queueDistribution, runningDistribution);
    }

    private Map<String, Integer> routeQueries(String groupName, int numRequests)
    {
        String eligibleBackend;
        int sum = 0;
        Map<String, Integer> routingDistribution = new HashMap<String, Integer>();

        for (int i = 0; i < numRequests; i++) {
            eligibleBackend = routingTable.getEligibleBackEnd(groupName, null);

            if (!routingDistribution.containsKey(eligibleBackend)) {
                routingDistribution.put(eligibleBackend, 1);
            }
            else {
                sum = routingDistribution.get(eligibleBackend) + 1;
                routingDistribution.put(eligibleBackend, sum);
            }
        }
        return routingDistribution;
    }

    @Test
    public void testRoutingWithEvenWeightDistribution()
    {
        int queueDistribution = 3;
        int runningDistribution = 0;

        for (int numRequests : QUERY_VOLUMES) {
            for (int numBk = 1; numBk <= NUM_BACKENDS; numBk++) {
                resetBackends(mockRoutingGroup, numBk, queueDistribution, runningDistribution);
                Map<String, Integer> routingDistribution = routeQueries(mockRoutingGroup, numRequests);

                // Useful for debugging
                //System.out.println("Input :" + clusterQueueMap.toString() + " Num of Requests:"
                //    + numRequests
                //    + " Internal Routing table: "
                //    + routingTable.getInternalWeightedRoutingTable(mockRoutingGroup).toString()
                //    + " Distribution: " + routingDistribution.toString());
                if (numBk > 1) {
                    if (routingDistribution.containsKey(mockRoutingGroup + (numBk - 1))) {
                        assertThat(routingDistribution.get(mockRoutingGroup + (numBk - 1)) <= Math.ceil(numRequests / numBk))
                                .isTrue();
                    }
                    else {
                        assertThat(routingDistribution.values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(numRequests);
                    }
                }
                else {
                    assertThat(routingDistribution.get(mockRoutingGroup + '0')).isEqualTo(Integer.valueOf(numRequests));
                }
            }
        }
    }

    @Test
    public void testRoutingWithSkewedWeightDistribution()
    {
        for (int numRequests : QUERY_VOLUMES) {
            for (int numBk = 1; numBk <= NUM_BACKENDS; numBk++) {
                deactiveAllBackends();
                registerBackEndsWithRandomQueueLengths(mockRoutingGroup, numBk);

                Map<String, Integer> routingDistribution = routeQueries(mockRoutingGroup, numRequests);

                // Useful Debugging Info
                //System.out.println("Input :" + clusterQueueMap.toString() + " Num of Requests:"
                //    + numRequests
                //    + " Internal Routing table: "
                //    + routingTable.getInternalWeightedRoutingTable(mockRoutingGroup).toString()
                //    + " Distribution: " + routingDistribution.toString());
                if (numBk > 2 && routingDistribution.containsKey(mockRoutingGroup + (numBk - 1))) {
                    assertThat(routingDistribution.get(mockRoutingGroup + (numBk - 1)) <= Math.ceil(numRequests / numBk))
                            .isTrue();
                }
                else {
                    assertThat(routingDistribution.values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(numRequests);
                }
            }
        }
    }

    @Test
    public void testRoutingWithUserQueuedLength()
    {
        int numBackends = 2;
        int queryVolume = 10000;

        // Case 1: All user queue counts Present.
        // Validate always routed to  cluster with lowest user queue
        registerBackEndsWithUserQueue(mockRoutingGroup, numBackends, Arrays.asList(1, 2));
        for (int i = 0; i < queryVolume; i++) {
            assertThat(routingTable.getEligibleBackEnd(mockRoutingGroup, mockUser)).isEqualTo(mockRoutingGroup + "0");
        }

        // Case 2: Not all user queue counts Present.
        // Validate always routed to cluster with zero queue length i.e. the missing cluster.
        registerBackEndsWithUserQueue(mockRoutingGroup, numBackends, List.of(1));
        for (int i = 0; i < queryVolume; i++) {
            assertThat(routingTable.getEligibleBackEnd(mockRoutingGroup, mockUser)).isEqualTo(mockRoutingGroup + "1");
        }

        // Case 3: All user queue counts Present but equal
        // Validate equally routed to all clusters.
        registerBackEndsWithUserQueue(mockRoutingGroup, numBackends, Arrays.asList(2, 2));
        Map<String, Integer> counts = new HashMap<>();
        for (int i = 0; i < queryVolume; i++) {
            String cluster = routingTable.getEligibleBackEnd(mockRoutingGroup, mockUser);
            counts.put(cluster, counts.getOrDefault(cluster, 0) + 1);
        }
        double variance = 0.1;
        double expectedLowerBound = (queryVolume / numBackends) * (1 - variance);
        double expectedUpperBound = (queryVolume / numBackends) * (1 + variance);

        for (Integer c : counts.values()) {
            assertThat(c >= expectedLowerBound && c <= expectedUpperBound).isTrue();
        }

        // Case 4: NO user queue lengths present
        // Validate equally routed to all clusters.
        registerBackEndsWithUserQueue(mockRoutingGroup, numBackends, new ArrayList<>());
        counts = new HashMap<>();
        for (int i = 0; i < queryVolume; i++) {
            String cluster = routingTable.getEligibleBackEnd(mockRoutingGroup, mockUser);
            counts.put(cluster, counts.getOrDefault(cluster, 0) + 1);
        }
        for (Integer c : counts.values()) {
            assertThat(c >= expectedLowerBound && c <= expectedUpperBound).isTrue();
        }

        // Case 5: Null or empty users
        // Validate equally routed to all clusters.
        registerBackEndsWithUserQueue(mockRoutingGroup, numBackends, new ArrayList<>());
        counts = new HashMap<>();
        for (int i = 0; i < queryVolume; i++) {
            String cluster = routingTable.getEligibleBackEnd(mockRoutingGroup, null);
            counts.put(cluster, counts.getOrDefault(cluster, 0) + 1);
        }
        for (Integer c : counts.values()) {
            assertThat(c >= expectedLowerBound && c <= expectedUpperBound).isTrue();
        }
    }

    @Test
    public void testRoutingWithEqualWeightDistribution()
    {
        int queueDistribution = 0;
        int runningDistribution = 0;
        for (int numRequests : QUERY_VOLUMES) {
            for (int numBk = 1; numBk <= NUM_BACKENDS; numBk++) {
                resetBackends(mockRoutingGroup, numBk, queueDistribution, runningDistribution);
                Map<String, Integer> routingDistribution = routeQueries(mockRoutingGroup, numRequests);

                //Useful Debugging Info
                //System.out.println("Input :" + clusterQueueMap.toString() + " Num of Requests:" +
                //numRequests
                //+ " Internal Routing table: " + routingTable.getInternalWeightedRoutingTable
                //(mockRoutingGroup).toString()
                //+ " Distribution: " + routingDistribution.toString());

                if (numBk > 1) {
                    // With equal weights, the algorithm randomly chooses from the list. Check that the
                    // distribution spans atleast half of the routing group.
                    assertThat(routingDistribution.size() >= clusterQueueMap.row(mockRoutingGroup).size() / 2).isTrue();
                }
                else {
                    assertThat(routingDistribution.get(mockRoutingGroup + '0')).isEqualTo(Integer.valueOf(numRequests));
                }
            }
        }
    }

    @Test
    public void testRoutingWithEqualQueueSkewedRunningDistribution()
    {
        int queueDistribution = 0;
        int runningDistribution = 100;

        for (int numRequests : QUERY_VOLUMES) {
            for (int numBk = 1; numBk <= NUM_BACKENDS; numBk++) {
                resetBackends(mockRoutingGroup, numBk, queueDistribution, runningDistribution);
                Map<String, Integer> routingDistribution = routeQueries(mockRoutingGroup, numRequests);

                //Useful Debugging Info
        /*
        System.out.println("Input :" + clusterRunningMap.toString() + " Num of Requests:" +
        numRequests
        + " Internal Routing table: " + routingTable.getInternalWeightedRoutingTable
        (mockRoutingGroup).toString()
        + " Distribution: " + routingDistribution.toString());
        */
                if (numBk > 2 && routingDistribution.containsKey(mockRoutingGroup + (numBk - 1))) {
                    assertThat(routingDistribution.get(mockRoutingGroup + (numBk - 1)) <= Math.ceil(numRequests / numBk))
                            .isTrue();
                }
                else {
                    assertThat(routingDistribution.values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(numRequests);
                }
            }
        }
    }

    @Test
    public void testRoutingWithMultipleGroups()
    {
        int queueDistribution = 10;
        int numRequests = 15;
        int numBk = 3;

        for (String grp : mockRoutingGroups) {
            resetBackends(grp, numBk, queueDistribution, 0);
            Map<String, Integer> routingDistribution = routeQueries(grp, numRequests);

            // Useful for debugging
            //System.out.println("Input :" + clusterQueueMap.toString() + " Num of Requests:" +
            //numRequests
            //+ " Internal Routing table: " + routingTable.getInternalWeightedRoutingTable
            //(grp).toString()
            //+ " Distribution: " + routingDistribution.toString());
            if (numBk > 1) {
                if (routingDistribution.containsKey(grp + (numBk - 1))) {
                    assertThat(routingDistribution.get(grp + (numBk - 1)) <= Math.ceil(numRequests / numBk))
                            .isTrue();
                }
                else {
                    assertThat(routingDistribution.values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(numRequests);
                }
            }
            else {
                assertThat(routingDistribution.get(grp + '0')).isEqualTo(Integer.valueOf(numRequests));
            }
        }
    }

    @Test
    public void testActiveClusterMonitorUpdateAndRouting()
            throws InterruptedException
    {
        int numRequests = 10;
        int numBatches = 10;
        int sum = 0;
        int numBk = 3;

        AtomicBoolean globalToggle = new AtomicBoolean(true);
        Map<String, Integer> routingDistribution = new HashMap<>();
        Map<String, Integer> totalDistribution = new HashMap<>();

        ScheduledExecutorService scheduler =
                Executors.newScheduledThreadPool(1);

        final Runnable activeClusterMonitor = () -> {
            String backend;
            int queueLen;
            for (int i = 0; i < numBk; i++) {
                backend = mockRoutingGroup + i;
                if (globalToggle.get()) {
                    queueLen = (i < Math.ceil((float) numBk / 2)) ? 0 : 100;
                }
                else {
                    queueLen = (i < Math.floor((float) numBk / 2)) ? 100 : 75;
                }
                clusterQueueMap.put(mockRoutingGroup, backend, queueLen);
            }
            globalToggle.set(!globalToggle.get());

            routingTable.updateRoutingTable(clusterQueueMap, clusterQueueMap, null);
        };

        resetBackends(mockRoutingGroup, numBk, 0, 0);
        scheduler.scheduleAtFixedRate(activeClusterMonitor, 0, 1, SECONDS);

        for (int batch = 0; batch < numBatches; batch++) {
            routingDistribution = routeQueries(mockRoutingGroup, numRequests);
            if (batch == 0) {
                totalDistribution.putAll(routingDistribution);
            }
            else {
                for (String key : routingDistribution.keySet()) {
                    sum = totalDistribution.getOrDefault(key, 0) + routingDistribution.get(key);
                    totalDistribution.put(key, sum);
                }
            }
            // Some random amount of sleep time
            Thread.sleep(270);
        }

        System.out.println("Total Requests :" + numBatches * numRequests
                + " distribution :" + totalDistribution);
        assertThat(totalDistribution.get(mockRoutingGroup + (numBk - 1)) <= (numBatches * numRequests / numBk))
                .isTrue();
        scheduler.shutdown();
    }
}
