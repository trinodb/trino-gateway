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

import com.google.inject.Inject;
import io.dropwizard.lifecycle.Managed;
import io.trino.gateway.ha.config.MonitorConfiguration;
import io.trino.gateway.ha.config.ProxyBackendConfiguration;
import io.trino.gateway.ha.router.BackendStateManager;
import io.trino.gateway.ha.router.GatewayBackendManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class ActiveClusterMonitor
        implements Managed
{
    public static final int BACKEND_CONNECT_TIMEOUT_SECONDS = 15;
    public static final int MONITOR_TASK_DELAY_MIN = 1;
    public static final int DEFAULT_THREAD_POOL_SIZE = 20;
    private static final Logger log = LoggerFactory.getLogger(ActiveClusterMonitor.class);

    private final List<TrinoClusterStatsObserver> clusterStatsObservers;
    private final GatewayBackendManager gatewayBackendManager;
    private final BackendStateManager backendStateManager;
    private final int connectionTimeout;
    private final int taskDelayMin;
    private final ClusterStatsMonitor clusterStatsMonitor;
    private volatile boolean monitorActive = true;
    private final ExecutorService executorService = Executors.newFixedThreadPool(DEFAULT_THREAD_POOL_SIZE);
    private final ExecutorService singleTaskExecutor = Executors.newSingleThreadExecutor();

    @Inject
    public ActiveClusterMonitor(
            List<TrinoClusterStatsObserver> clusterStatsObservers,
            GatewayBackendManager gatewayBackendManager,
            MonitorConfiguration monitorConfiguration,
            BackendStateManager backendStateManager,
            ClusterStatsMonitor clusterStatsMonitor)
    {
        this.clusterStatsObservers = clusterStatsObservers;
        this.gatewayBackendManager = gatewayBackendManager;
        this.connectionTimeout = monitorConfiguration.getConnectionTimeout();
        this.taskDelayMin = monitorConfiguration.getTaskDelayMin();
        this.backendStateManager = backendStateManager;
        this.clusterStatsMonitor = clusterStatsMonitor;
        log.info("Running cluster monitor with connection timeout of {} and task delay of {}",
                connectionTimeout, taskDelayMin);
    }

    /**
     * Run an app that queries all active trino clusters for stats.
     */
    public void start()
    {
        singleTaskExecutor.submit(
                () -> {
                    while (monitorActive) {
                        try {
                            List<ProxyBackendConfiguration> activeClusters =
                                    gatewayBackendManager.getAllActiveBackends();
                            List<Future<ClusterStats>> futures = new ArrayList<>();
                            for (ProxyBackendConfiguration backend : activeClusters) {
                                Future<ClusterStats> call =
                                        executorService.submit(() -> clusterStatsMonitor.monitor(backend));
                                futures.add(call);
                            }
                            List<ClusterStats> stats = new ArrayList<>();
                            for (Future<ClusterStats> clusterStatsFuture : futures) {
                                ClusterStats clusterStats = clusterStatsFuture.get();
                                stats.add(clusterStats);
                            }

                            if (clusterStatsObservers != null) {
                                for (TrinoClusterStatsObserver observer : clusterStatsObservers) {
                                    observer.observe(stats);
                                }
                            }
                        }
                        catch (Exception e) {
                            log.error("Error performing backend monitor tasks", e);
                        }
                        try {
                            Thread.sleep(TimeUnit.MINUTES.toMillis(taskDelayMin));
                        }
                        catch (Exception e) {
                            log.error("Error with monitor task", e);
                        }
                    }
                });
    }

    /**
     * Shut down the app.
     */
    public void stop()
    {
        this.monitorActive = false;
        this.executorService.shutdown();
        this.singleTaskExecutor.shutdown();
    }
}
