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

import io.airlift.units.Duration;
import io.trino.gateway.ha.config.MonitorConfiguration;
import io.trino.gateway.ha.config.ProxyBackendConfiguration;
import io.trino.gateway.ha.router.GatewayBackendManager;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class TestActiveClusterMonitor
{
    private static final int THREAD_POOL_SIZE = new MonitorConfiguration().getThreadPoolSize();

    @Test
    void testPublishStatsAsEachCheckCompletes()
            throws Exception
    {
        CountDownLatch slowCheckRelease = new CountDownLatch(1);
        ClusterStatsMonitor statsMonitor = backend -> {
            if (backend.getName().equals("slow")) {
                await(slowCheckRelease);
            }
            return healthyClusterStats(backend);
        };
        BlockingQueue<ClusterStats> observed = new LinkedBlockingQueue<>();

        ActiveClusterMonitor monitor = createMonitor(List.of(createBackend("slow"), createBackend("fast")), statsMonitor, observed);
        monitor.start();
        try {
            ClusterStats first = observed.poll(10, SECONDS);
            assertThat(first).isNotNull();
            assertThat(first.clusterId()).isEqualTo("fast");
            assertThat(monitor.isInitialized()).isFalse();

            slowCheckRelease.countDown();
            ClusterStats second = observed.poll(10, SECONDS);
            assertThat(second).isNotNull();
            assertThat(second.clusterId()).isEqualTo("slow");
        }
        finally {
            slowCheckRelease.countDown();
            monitor.stop();
        }
    }

    @Test
    void testFailedCheckReportedAsUnhealthy()
            throws Exception
    {
        ClusterStatsMonitor statsMonitor = backend -> {
            if (backend.getName().equals("failing")) {
                throw new IllegalStateException("test failure");
            }
            return healthyClusterStats(backend);
        };
        BlockingQueue<ClusterStats> observed = new LinkedBlockingQueue<>();

        ActiveClusterMonitor monitor = createMonitor(List.of(createBackend("failing"), createBackend("healthy")), statsMonitor, observed);
        monitor.start();
        try {
            assertThat(observedStatuses(observed, 2)).isEqualTo(Map.of(
                    "failing", TrinoStatus.UNHEALTHY,
                    "healthy", TrinoStatus.HEALTHY));
        }
        finally {
            monitor.stop();
        }
    }

    @Test
    void testCheckExceedingTaskDelayReportedAsUnhealthy()
            throws Exception
    {
        CountDownLatch hangingCheckInterrupted = new CountDownLatch(1);
        ClusterStatsMonitor statsMonitor = backend -> {
            if (backend.getName().equals("hanging")) {
                try {
                    new CountDownLatch(1).await();
                }
                catch (InterruptedException e) {
                    hangingCheckInterrupted.countDown();
                    Thread.currentThread().interrupt();
                }
            }
            return healthyClusterStats(backend);
        };
        BlockingQueue<ClusterStats> observed = new LinkedBlockingQueue<>();
        MonitorConfiguration monitorConfiguration = new MonitorConfiguration();
        monitorConfiguration.setTaskDelay(new Duration(500, MILLISECONDS));

        ActiveClusterMonitor monitor = createMonitor(List.of(createBackend("hanging"), createBackend("healthy")), statsMonitor, observed, monitorConfiguration);
        monitor.start();
        try {
            assertThat(observedStatuses(observed, 2)).isEqualTo(Map.of(
                    "hanging", TrinoStatus.UNHEALTHY,
                    "healthy", TrinoStatus.HEALTHY));
            assertThat(hangingCheckInterrupted.await(10, SECONDS)).isTrue();
            assertThat(monitor.isInitialized()).isTrue();
        }
        finally {
            monitor.stop();
        }
    }

    @Test
    void testCheckNotStartedWithinTaskDelayKeepsStatus()
            throws Exception
    {
        AtomicBoolean hang = new AtomicBoolean(true);
        ClusterStatsMonitor statsMonitor = backend -> {
            if (backend.getName().startsWith("hanging") && hang.get()) {
                awaitInterruption();
            }
            return healthyClusterStats(backend);
        };
        BlockingQueue<ClusterStats> observed = new LinkedBlockingQueue<>();

        // Occupy all monitor threads, so that the check of the queued backend does not start
        ActiveClusterMonitor monitor = createMonitor(saturatedBackends(), statsMonitor, observed, shortTaskDelay());
        monitor.start();
        try {
            for (int i = 0; i < THREAD_POOL_SIZE; i++) {
                ClusterStats stats = observed.poll(10, SECONDS);
                assertThat(stats).isNotNull();
                assertThat(stats.clusterId()).startsWith("hanging");
                assertThat(stats.trinoStatus()).isEqualTo(TrinoStatus.UNHEALTHY);
            }
            hang.set(false);
            ClusterStats queued = null;
            while (queued == null) {
                ClusterStats stats = observed.poll(10, SECONDS);
                assertThat(stats).isNotNull();
                if (stats.clusterId().equals("queued")) {
                    queued = stats;
                }
            }
            assertThat(queued.trinoStatus()).isEqualTo(TrinoStatus.HEALTHY);
        }
        finally {
            monitor.stop();
        }
    }

    @Test
    void testCheckStartedAfterDeadlineNotReportedAsUnhealthy()
            throws Exception
    {
        AtomicBoolean hang = new AtomicBoolean(true);
        CountDownLatch queuedCheckStarted = new CountDownLatch(1);
        ClusterStatsMonitor statsMonitor = backend -> {
            if (backend.getName().equals("queued")) {
                queuedCheckStarted.countDown();
            }
            if (hang.get()) {
                awaitInterruption();
            }
            return healthyClusterStats(backend);
        };
        BlockingQueue<ClusterStats> observed = new LinkedBlockingQueue<>();
        // Cancelling the first hanging check frees a monitor thread, which starts the queued check.
        // Hold the cleanup until the queued check runs, so that the cleanup cancels a check started after the deadline.
        AtomicBoolean firstPublish = new AtomicBoolean(true);
        TrinoClusterStatsObserver observer = stats -> {
            if (firstPublish.getAndSet(false)) {
                await(queuedCheckStarted);
            }
            observed.addAll(stats);
        };

        ActiveClusterMonitor monitor = createMonitor(saturatedBackends(), statsMonitor, observer, shortTaskDelay());
        monitor.start();
        try {
            // The first cycle reports only the hanging backends, and the next cycle starts with them again
            for (int i = 0; i <= THREAD_POOL_SIZE; i++) {
                ClusterStats stats = observed.poll(10, SECONDS);
                assertThat(stats).isNotNull();
                assertThat(stats.clusterId()).startsWith("hanging");
                assertThat(stats.trinoStatus()).isEqualTo(TrinoStatus.UNHEALTHY);
            }
            assertThat(queuedCheckStarted.getCount()).isZero();
        }
        finally {
            hang.set(false);
            monitor.stop();
        }
    }

    @Test
    void testCheckCompletedDuringCleanupIsPublished()
            throws Exception
    {
        CountDownLatch queuedCheckCompleted = new CountDownLatch(1);
        ClusterStatsMonitor statsMonitor = backend -> {
            if (backend.getName().equals("queued")) {
                queuedCheckCompleted.countDown();
                return healthyClusterStats(backend);
            }
            awaitInterruption();
            return healthyClusterStats(backend);
        };
        BlockingQueue<ClusterStats> observed = new LinkedBlockingQueue<>();
        // Hold the cleanup until the queued check, started by the thread freed by the first cancellation, completes
        AtomicBoolean firstPublish = new AtomicBoolean(true);
        TrinoClusterStatsObserver observer = stats -> {
            if (firstPublish.getAndSet(false)) {
                await(queuedCheckCompleted);
                // The future completes right after the check returns
                sleep(100);
            }
            observed.addAll(stats);
        };

        ActiveClusterMonitor monitor = createMonitor(saturatedBackends(), statsMonitor, observer, shortTaskDelay());
        monitor.start();
        try {
            for (int i = 0; i < THREAD_POOL_SIZE; i++) {
                ClusterStats stats = observed.poll(10, SECONDS);
                assertThat(stats).isNotNull();
                assertThat(stats.clusterId()).startsWith("hanging");
                assertThat(stats.trinoStatus()).isEqualTo(TrinoStatus.UNHEALTHY);
            }
            // Published in the same cycle, after the hanging backends
            ClusterStats queued = observed.poll(10, SECONDS);
            assertThat(queued).isNotNull();
            assertThat(queued.clusterId()).isEqualTo("queued");
            assertThat(queued.trinoStatus()).isEqualTo(TrinoStatus.HEALTHY);
        }
        finally {
            monitor.stop();
        }
    }

    @Test
    void testThreadPoolSizeLetsAllChecksStart()
            throws Exception
    {
        ClusterStatsMonitor statsMonitor = backend -> {
            if (backend.getName().startsWith("hanging")) {
                awaitInterruption();
            }
            return healthyClusterStats(backend);
        };
        BlockingQueue<ClusterStats> observed = new LinkedBlockingQueue<>();
        List<ProxyBackendConfiguration> backends = saturatedBackends();
        MonitorConfiguration monitorConfiguration = shortTaskDelay();
        monitorConfiguration.setThreadPoolSize(backends.size());

        // With a monitor thread for every backend, the check of the queued backend starts right away
        ActiveClusterMonitor monitor = createMonitor(backends, statsMonitor, observed, monitorConfiguration);
        monitor.start();
        try {
            Map<String, TrinoStatus> statuses = observedStatuses(observed, backends.size());
            assertThat(statuses).hasSize(backends.size());
            assertThat(statuses).containsEntry("queued", TrinoStatus.HEALTHY);
            assertThat(statuses).containsEntry("hanging0", TrinoStatus.UNHEALTHY);
        }
        finally {
            monitor.stop();
        }
    }

    @Test
    void testShuffledChecksStartEventually()
            throws Exception
    {
        ClusterStatsMonitor statsMonitor = backend -> {
            if (backend.getName().startsWith("hanging")) {
                awaitInterruption();
            }
            // Like a real check, the check of the queued backend takes longer than the cleanup after the deadline,
            // so it only completes when it starts before the deadline
            sleep(100);
            return healthyClusterStats(backend);
        };
        BlockingQueue<ClusterStats> observed = new LinkedBlockingQueue<>();
        GatewayBackendManager backendManager = mock(GatewayBackendManager.class);
        when(backendManager.getAllBackends()).thenReturn(saturatedBackends());

        // The hanging backends occupy all monitor threads, but the shuffled order lets the queued backend start in a later cycle
        ActiveClusterMonitor monitor = new ActiveClusterMonitor(Set.of(observed::addAll), backendManager, shortTaskDelay(), statsMonitor);
        monitor.start();
        try {
            // Each cycle leaves the queued backend out with a probability of 1 in 21
            long end = System.nanoTime() + SECONDS.toNanos(30);
            ClusterStats queued = null;
            while (queued == null && System.nanoTime() < end) {
                ClusterStats stats = observed.poll(10, SECONDS);
                assertThat(stats).isNotNull();
                if (stats.clusterId().equals("queued")) {
                    queued = stats;
                }
            }
            assertThat(queued).isNotNull();
            assertThat(queued.trinoStatus()).isEqualTo(TrinoStatus.HEALTHY);
        }
        finally {
            monitor.stop();
        }
    }

    // Enough hanging backends to occupy all monitor threads, followed by a queued backend
    private static List<ProxyBackendConfiguration> saturatedBackends()
    {
        List<ProxyBackendConfiguration> backends = new ArrayList<>();
        for (int i = 0; i < THREAD_POOL_SIZE; i++) {
            backends.add(createBackend("hanging" + i));
        }
        backends.add(createBackend("queued"));
        return backends;
    }

    private static MonitorConfiguration shortTaskDelay()
    {
        MonitorConfiguration monitorConfiguration = new MonitorConfiguration();
        monitorConfiguration.setTaskDelay(new Duration(500, MILLISECONDS));
        return monitorConfiguration;
    }

    private static void awaitInterruption()
    {
        try {
            new CountDownLatch(1).await();
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void sleep(long millis)
    {
        try {
            Thread.sleep(millis);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    private static ActiveClusterMonitor createMonitor(
            List<ProxyBackendConfiguration> backends,
            ClusterStatsMonitor statsMonitor,
            TrinoClusterStatsObserver observer,
            MonitorConfiguration monitorConfiguration)
    {
        GatewayBackendManager backendManager = mock(GatewayBackendManager.class);
        when(backendManager.getAllBackends()).thenReturn(backends);
        // Keep the order of the backends, so that the tests control which checks wait for a monitor thread
        return new ActiveClusterMonitor(Set.of(observer), backendManager, monitorConfiguration, statsMonitor, _ -> {});
    }

    private static ActiveClusterMonitor createMonitor(
            List<ProxyBackendConfiguration> backends,
            ClusterStatsMonitor statsMonitor,
            BlockingQueue<ClusterStats> observed)
    {
        return createMonitor(backends, statsMonitor, observed, new MonitorConfiguration());
    }

    private static ActiveClusterMonitor createMonitor(
            List<ProxyBackendConfiguration> backends,
            ClusterStatsMonitor statsMonitor,
            BlockingQueue<ClusterStats> observed,
            MonitorConfiguration monitorConfiguration)
    {
        GatewayBackendManager backendManager = mock(GatewayBackendManager.class);
        when(backendManager.getAllBackends()).thenReturn(backends);
        TrinoClusterStatsObserver observer = observed::addAll;
        // Keep the order of the backends, so that the tests control which checks wait for a monitor thread
        return new ActiveClusterMonitor(Set.of(observer), backendManager, monitorConfiguration, statsMonitor, _ -> {});
    }

    private static Map<String, TrinoStatus> observedStatuses(BlockingQueue<ClusterStats> observed, int count)
            throws InterruptedException
    {
        Map<String, TrinoStatus> statuses = new HashMap<>();
        for (int i = 0; i < count; i++) {
            ClusterStats stats = observed.poll(10, SECONDS);
            assertThat(stats).isNotNull();
            statuses.put(stats.clusterId(), stats.trinoStatus());
        }
        return statuses;
    }

    private static void await(CountDownLatch latch)
    {
        try {
            latch.await();
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    private static ClusterStats healthyClusterStats(ProxyBackendConfiguration backend)
    {
        return ClusterStats.builder(backend.getName())
                .trinoStatus(TrinoStatus.HEALTHY)
                .build();
    }

    private static ProxyBackendConfiguration createBackend(String name)
    {
        ProxyBackendConfiguration backend = new ProxyBackendConfiguration();
        backend.setName(name);
        backend.setProxyTo("http://%s.example.com:8080".formatted(name));
        return backend;
    }
}
