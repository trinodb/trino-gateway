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
                try {
                    new CountDownLatch(1).await();
                }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return healthyClusterStats(backend);
        };
        // Occupy all monitor threads, so that the check of the queued backend does not start
        List<ProxyBackendConfiguration> backends = new ArrayList<>();
        for (int i = 0; i < ActiveClusterMonitor.DEFAULT_THREAD_POOL_SIZE; i++) {
            backends.add(createBackend("hanging" + i));
        }
        backends.add(createBackend("queued"));
        BlockingQueue<ClusterStats> observed = new LinkedBlockingQueue<>();
        MonitorConfiguration monitorConfiguration = new MonitorConfiguration();
        monitorConfiguration.setTaskDelay(new Duration(500, MILLISECONDS));

        ActiveClusterMonitor monitor = createMonitor(backends, statsMonitor, observed, monitorConfiguration);
        monitor.start();
        try {
            for (int i = 0; i < ActiveClusterMonitor.DEFAULT_THREAD_POOL_SIZE; i++) {
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
        return new ActiveClusterMonitor(Set.of(observer), backendManager, monitorConfiguration, statsMonitor);
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
