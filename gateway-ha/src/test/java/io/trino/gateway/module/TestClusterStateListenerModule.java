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
package io.trino.gateway.module;

import io.dropwizard.core.setup.Environment;
import io.trino.gateway.ha.clustermonitor.HealthChecker;
import io.trino.gateway.ha.clustermonitor.TrinoClusterStatsObserver;
import io.trino.gateway.ha.config.HaGatewayConfiguration;
import io.trino.gateway.ha.config.MonitorConfiguration;
import io.trino.gateway.ha.config.NotifierConfiguration;
import io.trino.gateway.ha.module.ClusterStateListenerModule;
import io.trino.gateway.ha.router.BackendStateManager;
import io.trino.gateway.ha.router.RoutingManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestClusterStateListenerModule
{
    @Test
    public void testClusterStateListenerModule()
    {
        HaGatewayConfiguration config = mock(HaGatewayConfiguration.class);
        MonitorConfiguration monitorConfig = mock(MonitorConfiguration.class);
        NotifierConfiguration notifierConfiguration = mock(NotifierConfiguration.class);
        when(config.getMonitor()).thenReturn(monitorConfig);
        when(config.getNotifier()).thenReturn(notifierConfiguration);
        when(notifierConfiguration.isEnabled()).thenReturn(true);

        RoutingManager routingManager = mock(RoutingManager.class);
        BackendStateManager backendStateManager = mock(BackendStateManager.class);

        ClusterStateListenerModule module = new ClusterStateListenerModule(config, mock(Environment.class));

        List<TrinoClusterStatsObserver> observers = module.getClusterStatsObservers(routingManager, backendStateManager);

        assertThat(observers).hasSize(3);
        assertThat(observers.get(2)).isInstanceOf(HealthChecker.class);
        assertThat(module.getMonitorConfiguration()).isEqualTo(monitorConfig);
    }
}
